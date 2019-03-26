#!/usr/bin/env groovy

pipeline {

        options { buildDiscarder(logRotator(numToKeepStr: '10')) }

	agent {
		label 'map-jenkins'
	}

	stages {
		stage('Init') {
			steps {
				echo "NODE_NAME = ${env.NODE_NAME}"
			}
		}
		
		stage('Sloccount') {
		    steps {
		        sh "cloc --by-file --xml --out=cloc.xml src"
		        sloccountPublish pattern: 'cloc.xml' 
		    }
		}
		
		stage('Build and Test') {
			steps {
                          wrap([$class: 'Xvfb']) {                          
				timestamps {
				  timeout(time: 2, unit: 'HOURS') {
                                    sh script: "./continuous_integration/standard_build", returnStdout: true
				} // timeout
			    } // timestamps
                          } // Xvfb
			} // steps
		} // stage build & test
                
	} // stages
		
	post {
		always {
		  archiveArtifacts artifacts: '**/*.log,**/build/reports/**,**/build/test-results/**'

                    openTasks defaultEncoding: '', excludePattern: '', healthy: '', high: 'FIXME,HACK', low: '', normal: 'TODO', pattern: '**/*.java,**/*.sh,**/*.py', unHealthy: ''
			warnings categoriesPattern: '', consoleParsers: [[parserName: 'Java Compiler (javac)']], defaultEncoding: '', excludePattern: '', healthy: '', includePattern: '', messagesPattern: '', unHealthy: ''

                    findbugs pattern: '**/build/reports/findbugs/*.xml', unstableTotalAll: '0'

                    checkstyle pattern: '**/build/reports/checkstyle/*.xml', unstableTotalAll: '0'
                    
                    junit testResults: "**/build/test-results/**/*.xml", keepLongStdio: true
                    
			emailext recipientProviders: [[$class: 'DevelopersRecipientProvider'], [$class: 'CulpritsRecipientProvider']], 
					to: 'fill-in-email-address',
					subject: '$DEFAULT_SUBJECT', 
					body: '''${PROJECT_NAME} - Build # ${BUILD_NUMBER} - ${BUILD_STATUS}

Changes:
${CHANGES}

Failed Tests:
${FAILED_TESTS, onlyRegressions=false}

Check console output at ${BUILD_URL} to view the full results.

Tail of Log:
${BUILD_LOG, maxLines=50}

'''

		} // always
	} // post

} // pipeline
