def apps = readYaml file: 'apps.yaml'

apps.apps.each { app ->

    pipelineJob("deploy-${app.name}") {

        description("Auto‑generated job for ${app.name}")

        definition {
            cps {
                script("""
                    @Library('my-shared-lib') _
                    appPipeline(
                        appName: '${app.name}',
                        configFile: '${app.config}'
                    )
                """.stripIndent())
            }
        }
    }
}
