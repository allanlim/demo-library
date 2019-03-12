#!/bin/groovy
import org.kohsuke.github.GHCommitState;

def ghprb(branch) {
 properties([
      buildDiscarder(logRotator(artifactNumToKeepStr: '20', numToKeepStr: '20')),
      parameters([
        string(name: 'GIT_REPO', defaultValue: 'git@github.com:MyOrg/UI.git', description: 'Git repo'),
        [$class: 'GitParameterDefinition', branch: 'origin/master', branchFilter: '.*', defaultValue: '', description: 'Git tag', name: 'TAG', quickFilterEnabled: false, selectedValue: 'NONE', sortMode: 'DESCENDING_SMART', tagFilter: '*', type: 'PT_TAG'],
      ]),
      [$class: 'GithubProjectProperty', displayName: '', projectUrlStr: 'https://github.com/MyOrg/UI/'],
      [$class: 'org.jenkinsci.plugins.workflow.job.properties.PipelineTriggersJobProperty', triggers:[
        [
          $class: 'org.jenkinsci.plugins.ghprb.GhprbTrigger',
          orgslist: 'MyOrg',
          cron: 'H/5 * * * *',
          triggerPhrase: 'special trigger phrase',
          onlyTriggerPhrase: true,
          useGitHubHooks: true,
          permitAll: true,
          autoCloseFailedPullRequests: false,
          displayBuildErrorsOnDownstreamBuilds: true,
          extensions: [
            [
              $class: 'org.jenkinsci.plugins.ghprb.extensions.status.GhprbSimpleStatus',
              commitStatusContext: 'Docker Build',
              showMatrixStatus: false,
              triggeredStatus: 'Starting job...',
              startedStatus: 'Building...',
              completedStatus: [
                [$class: 'org.jenkinsci.plugins.ghprb.extensions.comments.GhprbBuildResultMessage', message: 'Done', result: GHCommitState.SUCCESS],
                [$class: 'org.jenkinsci.plugins.ghprb.extensions.comments.GhprbBuildResultMessage', message: 'Failed', result: GHCommitState.FAILURE]
              ]
            ]
          ]
        ]
      ]]
    ])
}
