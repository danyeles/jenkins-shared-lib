def call(Map args = [:]) {
    pipeline {
        agent any

        parameters {
            choice(name: 'ACTION', choices: ['Deploy', 'Update', 'Stop and Run'], description: 'Select action')
        }

        stages {
            stage('Load config') {
                steps {
                    script {
                        cfg = readYaml file: args.configFile
                    }
                }
            }

            stage('Pull Latest Image') {
                when {
                    expression { params.ACTION == 'Update' }
                }
                steps {
                    sh "docker pull ${cfg.image}:${cfg.tag}"
                }
            }

            stage('Check Existing Container') {
                steps {
                    script {
                        containerExists = sh(
                            script: "docker ps -aq -f name=${cfg.container}",
                            returnStdout: true
                        ).trim()
                    }
                }
            }

            stage('Perform Selected Action') {
                steps {
                    script {
                        if (params.ACTION == 'Update' && containerExists) {
                            echo "Updating container ${cfg.container}"
                            sh "docker stop ${cfg.container} || true"
                            sh "docker rm ${cfg.container} || true"
                            sh buildDockerRun(cfg)
                        } else if (params.ACTION == 'Stop and Run' && containerExists) {
                            echo "Restarting container ${cfg.container} without updating image"
                            sh "docker stop ${cfg.container} || true"
                            sh "docker start ${cfg.container}"
                        } else {
                            echo "Deploying new container ${cfg.container}"
                            sh buildDockerRun(cfg)
                        }
                    }
                }
            }
        }
    }
}

def buildDockerRun(cfg) {
    def cmd = [
        "docker run -d",
        "--restart ${cfg.restart}",
        "--name ${cfg.container}"
    ]

    cfg.ports.each { p ->
        cmd << "-p ${p}"
    }

    cfg.env.each { k, v ->
        cmd << "-e ${k}=${v}"
    }

    cfg.volumes.each { v ->
        cmd << "-v ${v}"
    }

    cmd << "${cfg.image}:${cfg.tag}"

    return cmd.join(" \\\n    ")
}

