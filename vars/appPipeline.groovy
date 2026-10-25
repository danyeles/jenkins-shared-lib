def call(Map args = [:]) {

    pipeline {
        agent any

        parameters {
            choice(
                name: 'ACTION',
                choices: ['Deploy', 'Update', 'Stop and Run', 'Delete'],
                description: 'Select the deployment action'
            )
        }

        stages {

            stage('Load Config') {
                steps {
                    script {
                        if (!args.configFile) {
                            error "configFile parameter is required"
                        }

                        // Load YAML from shared library resources
                        cfg = readYaml text: libraryResource(args.configFile)

                        // Utility job? Run a different pipeline.
                        if (cfg.utility) {
                            echo "Running utility pipeline for ${args.appName}"
                            runUtilityPipeline(cfg)
                            currentBuild.result = 'SUCCESS'
                            return
                        }

                        // Build full image reference
                        fullImage = "${cfg.image}:${cfg.tag ?: 'latest'}"

                        echo "Loaded config for ${args.appName}"
                        echo "Using image: ${fullImage}"
                    }
                }
            }

            stage('Pull Image (Update only)') {
                when { expression { params.ACTION == 'Update' && !cfg.utility } }
                steps {
                    sh "docker pull ${fullImage}"
                }
            }

            stage('Check Existing Container') {
                when { expression { !cfg.utility } }
                steps {
                    script {
                        env.EXISTING_CONTAINER_ID = sh(
                            script: "docker ps -aq --filter 'name=^/${cfg.container}\$'",
                            returnStdout: true
                        ).trim()

                        echo env.EXISTING_CONTAINER_ID ? "Container exists" : "Container does not exist"
                    }
                }
            }

            stage('Perform Action') {
                when { expression { !cfg.utility } }
                steps {
                    script {
                        def existingContainerId = env.EXISTING_CONTAINER_ID

                        //
                        // DELETE ACTION
                        //
                        if (params.ACTION == 'Delete') {
                            if (existingContainerId) {
                                echo "Deleting container ${cfg.container}"
                                sh """
                                    docker stop ${cfg.container} || true
                                    docker rm ${cfg.container} || true
                                """
                            } else {
                                echo "Container ${cfg.container} does not exist; nothing to delete"
                            }
                            return
                        }

                        //
                        // Build docker run command dynamically
                        //

                        def runCmd = []
                        runCmd << "docker run -d"
                        runCmd << "--name ${cfg.container}"
                        runCmd << "--restart ${cfg.restart ?: 'always'}"

                        // Ports
                        cfg.ports?.each { p ->
                            runCmd << "-p ${p}"
                        }

                        // Environment variables
                        cfg.env?.each { k, v ->
                            runCmd << "-e ${k}=${v}"
                        }

                        // Volumes
                        cfg.volumes?.each { v ->
                            runCmd << "-v ${v}"
                        }

                        // Devices
                        cfg.devices?.each { device ->
                            runCmd << "--device ${device}"
                        }

                        // Extra docker flags
                        if (cfg.docker_extra?.read_only) {
                            runCmd << "--read-only"
                        }

                        if (cfg.docker_extra?.stop_timeout) {
                            runCmd << "--stop-timeout ${cfg.docker_extra.stop_timeout}"
                        }

                        cfg.docker_extra?.tmpfs?.each { t ->
                            runCmd << "--tmpfs ${t}"
                        }

                        // Append image
                        runCmd << fullImage

                        def finalRunCmd = runCmd.join(" \\\n    ")

                        //
                        // ACTION HANDLING
                        //

                        if (params.ACTION == 'Update' && existingContainerId) {

                            echo "Updating container ${cfg.container}"

                            sh """
                                docker rm -f ${cfg.container}
                                ${finalRunCmd}
                            """

                        } else if (params.ACTION == 'Deploy' && existingContainerId) {

                            echo "Replacing existing container ${cfg.container} using the configured image"

                            sh """
                                docker rm -f ${cfg.container}
                                ${finalRunCmd}
                            """

                        } else if (params.ACTION == 'Stop and Run' && existingContainerId) {

                            echo "Restarting container ${cfg.container} without updating image"

                            sh """
                                docker stop ${cfg.container}
                                docker start ${cfg.container}
                            """

                        } else {

                            echo "Deploying new container ${cfg.container}"

                            sh """
                                ${finalRunCmd}
                            """
                        }
                    }
                }
            }
        }
    }
}


//
// UTILITY PIPELINE
//
def runUtilityPipeline(cfg) {

    pipeline {
        agent any

        stages {

            stage('Run Utility Task') {
                steps {
                    script {

                        if (cfg.container == null) {
                            error "Utility job requires 'container' in YAML"
                        }

                        echo "Running utility task for container: ${cfg.container}"

                        sh """
                        if ! docker ps -a --format '{{.Names}}' | grep -q '^${cfg.container}\$'; then
                            echo "ERROR: Container '${cfg.container}' not found."
                            exit 1
                        fi

                        echo "Extracting temporary admin password (if available)..."
                        docker logs ${cfg.container} 2>&1 | grep -i 'temporary password' \
                            || echo "No temporary password found. The container may have already been initialized."
                        """
                    }
                }
            }
        }
    }
}
