# pipeline-library
Jenkins shared library. 

Pré-requisitos
----------
* [Sonar](https://www.sonarqube.org/)
* [Sonar configurado com o jenkins](https://medium.com/@kaiofelixdeoliveira/integra%C3%A7%C3%A3o-continua-com-jenkins-e-sonarqube-9510ea7dfda9)
* Projeto java com uso de maven para build
* Uso de giltab como SCM - Pode ser utilizado com outros SCM's mas requer adaptação no código



Como usar
----------
* Faça um fork desse projeto
* Modifique o arquivo `Constants.groovy` de acordo com sua configuração.
* Adicione o repositorio com essa pipeline ao Jenkins: *Manage Jenkins > Configure System > Global Pipeline Libraries* e de o nome `pipeline-library`.
* Crie um arquivo `Jenkinsfile` no seu projeto java com o conteúdo abaixo:
    ```
    @Library('pipeline-library')
    import pipeline.jenkins.*

    def builder = new JenkinsPipelineBootstrap().createBuilder()

    String serviceName = 'my-service'
    String gitBranch = 'master'

    builder.mavenApplicationPipeline(serviceName, gitBranch)
    ```

* Crie um job no Jenkins com o seguintes parametros e configurações
    * Git Parameter [name: gitlabSourceBranch], parameter type: Branch]
    * Em `Build when a change is pushed to GitLab`, adicione o webhook ao projeto no gitlab
    * Em *Pipeline > SCM > Repository URL* Adicione o repositório do projeto no git
    * Em *Pipeline > SCM > Branch to build* coloque `${gitlabSourceBranch}`
            

* Realize um commit no projeto desejado.


Baseado no projeto [politie/pipeline-library](https://github.com/politie/pipeline-library)

