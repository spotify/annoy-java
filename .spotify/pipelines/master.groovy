@Grab(group='com.spotify', module='pipeline-conventions', version='1.0.2', changing=true)

import com.spotify.pipeline.Pipeline

new Pipeline(this) {{ build {
    notify.byMail(recipients: 'discovery+jenkins@spotify.com')
    group(name: 'Build') {
        maven.pipelineVersionFromPom()
        maven.run(goal: 'clean checkstyle:checkstyle test')
        jenkinsPipeline.inJob {
            publishers {
                checkstyle('**/checkstyle-result.xml') {
                    computeNew true
                    thresholds(failedNew: [all: 50])
                }
                archiveJunit('**/surefire-reports/*.xml')
                jacocoCodeCoverage {
                    exclusionPattern '**/*Test*,**/proto/*'
                }
            }
        }
    }
    group(name: 'Upload') {
        maven.upload()
    }
}}}
