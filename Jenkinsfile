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

		stage('P2Protelis') {
			steps {
				timestamps {
                                  dir("src/P2Protelis") {
                                    timeout(time: 30, unit: 'MINUTES') {
                                      sh "./gradlew --continue --no-daemon --gradle-user-home " + gradleRepo() + " -Dmaven.repo.local=" + mavenRepo() + " clean build publish -x test"
                                    }
                                  }
				}
			}
		}

		stage('MAP Agent') {
			steps {
				timestamps {
                                  dir("src/MAP-Agent") {
                                    timeout(time: 3, unit: 'HOURS') {
                                      sh "./gradlew -Dtest.ignoreFailures=true --continue --no-daemon --gradle-user-home " + gradleRepo() + " -Dmaven.repo.local=" + mavenRepo() + " clean build check publish"
                                    }
                                  }
				}
			}
		}

		stage('MAP Visualization') {
			steps {
                          wrap([$class: 'Xvfb']) {
				timestamps {
                                  dir("src/MAP-Visualization") {
                                    timeout(time: 1, unit: 'HOURS') {
                                      sh "./gradlew -Dtest.ignoreFailures=true --continue --no-daemon --gradle-user-home " + gradleRepo() + " -Dmaven.repo.local=" + mavenRepo() + " clean build check"
                                    }
                                  }
				}
                            }
			}
		}

		stage('MAP Chart Generation') {
			steps {
				timestamps {
                                  dir("src/MAP-ChartGeneration") {
                                    timeout(time: 1, unit: 'HOURS') {
                                      sh "./gradlew -Dtest.ignoreFailures=true --continue --no-daemon --gradle-user-home " + gradleRepo() + " -Dmaven.repo.local=" + mavenRepo() + " clean build check"
                                    }
                                  }
				}
			}
		}

		stage('MAP Demand Generation GUI') {
			steps {
				timestamps {
                                  dir("src/MAP-DemandGenerationGUI") {
                                    timeout(time: 1, unit: 'HOURS') {
                                      sh "./gradlew -Dtest.ignoreFailures=true --continue --no-daemon --gradle-user-home " + gradleRepo() + " -Dmaven.repo.local=" + mavenRepo() + " clean build check"
                                    }
                                  }
				}
			}
		}

                
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
					to: 'FILL-IN-EMAIL-HERE',
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

def gradleRepo() {
 "${WORKSPACE}/gradle-repo"
}

def mavenRepo() {
 "${WORKSPACE}/maven-repo"
}
