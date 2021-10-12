@Library('forge-shared-library')_

pipeline {
    agent any
    /* TODO: Custom docker image that is just java + docker + docker-compose?
    agent {
        docker {
            image 'gradle:jdk8'
        }
    }
    */
    environment {
        DISCORD_WEBHOOK = credentials('forge-discord-jenkins-webhook')
        DISCORD_PREFIX = "Job: Reposilite Branch: ${BRANCH_NAME} Build: #${BUILD_NUMBER}"
        JENKINS_HEAD = 'https://wiki.jenkins-ci.org/download/attachments/2916393/headshot.png'
    }
    stages {
        stage('notify_start') {
            when {
                not {
                    changeRequest()
                }
            }
            steps {
                discordSend(
                    title: "${DISCORD_PREFIX} Started",
                    successful: true,
                    result: 'ABORTED', //White border
                    thumbnail: JENKINS_HEAD,
                    webhookURL: DISCORD_WEBHOOK
                )
            }
        }
        stage('build') {
            when {
                not {
                    changeRequest()
                }
            }
            steps {
                sh 'curl -L "https://github.com/docker/compose/releases/download/1.29.2/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose'
                sh 'chmod +x /usr/local/bin/docker-compose'
                withGradle {
                    sh './gradlew ${GRADLE_ARGS} composeBuild'
                }
                script {
                    gradleVersion(this, ':backend:properties')
                }
            }
            post {
                success {
                    writeChangelog(currentBuild, 'build/changelog.txt')
                }
            }
        }
        /*
        stage('publish') {
            when {
                not {
                    changeRequest()
                }
            }
            steps {
                withCredentials([usernamePassword(credentialsId: 'maven-forge-user', usernameVariable: 'MAVEN_USER', passwordVariable: 'MAVEN_PASSWORD')]) {
                    withGradle {
                        sh './gradlew ${GRADLE_ARGS} :backend:publish'
                    }
                }
            }
             post {
                success {
                    build job: 'filegenerator', parameters: [string(name: 'COMMAND', value: "promote ${env.MYGROUP}:${env.MYARTIFACT} ${env.MYVERSION} latest")], propagate: false, wait: false
                }
            }
        }
        */
    }
    post {
        always {
            script {
                if (env.CHANGE_ID == null) { // This is unset for non-PRs
                    discordSend(
                        title: "${DISCORD_PREFIX} Finished ${currentBuild.currentResult}",
                        description: '```\n' + getChanges(currentBuild) + '\n```',
                        successful: currentBuild.resultIsBetterOrEqualTo("SUCCESS"),
                        result: currentBuild.currentResult,
                        thumbnail: JENKINS_HEAD,
                        webhookURL: DISCORD_WEBHOOK
                    )
                }
            }
        }
    }
}