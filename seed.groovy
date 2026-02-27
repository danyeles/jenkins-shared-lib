def apps = [
    [name: 'sonarr', config: 'configs/sonarr.yaml']
]

apps.each { app ->
    pipelineJob("deploy-${app.name}") {
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
