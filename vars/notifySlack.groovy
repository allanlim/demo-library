#!/usr/bin/env groovy

/**
* notify slack and set message based on build status
*/
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.model.Actionable;



def call(String buildStatus = 'STARTED', String channel = '#engineering') {

  // buildStatus of null means success
  buildStatus = buildStatus ?: 'SUCCESS'
  channel = channel ?: '#engineering'

  // Default values
  def colorName = 'GREEN'
  def colorCode = '#2EB886'
  def subject = "${buildStatus}: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]'"
  def title = "${env.JOB_NAME} Build: ${env.BUILD_NUMBER}"
  def title_link = "${env.RUN_DISPLAY_URL}"
  //def branchName = "${env.GIT_BRANCH}"
  def branchName = "${ghprbTargetBranch}"
  
  def commit = "${env.GIT_COMMIT}"
  //def author = bat(script: "@echo off\ngit log -n 1 ${env.GIT_COMMIT} --format=%%aN", returnStdout: true).trim()
  def author = "${ghprbActualCommitAuthor}"
  //def committer = bat(script: "@echo off\ngit log -n 1 ${env.GIT_COMMIT} --format=%%cN", returnStdout: true).trim()
  def message = "${ghprbPullLongDescription}"
  def pullTitle = "${ghprbPullTitle}"
  def commits = getChangeString()
  
  // If a manual re-build is triggered the original commit author will be blank, this serves as a check to add an author
  if (author == "") {
    author = bat(script: "@echo off\ngit log -n 1 ${env.GIT_COMMIT} --format=%%aN", returnStdout: true).trim()
  } 
  
  // Override default values based on build status
  if (buildStatus == 'STARTED') {
    color = 'GREEN'
    colorCode = '#2EB886'
  } else if (buildStatus == 'SUCCESS') {
    color = 'GREEN'
    colorCode = '#2EB886'
  } else if (buildStatus == 'UNSTABLE') {
    color = 'YELLOW'
    colorCode = '#DAA038'
  } else {
    color = 'RED'
    colorCode = '#A30200'
  }
  
  // Get test results for slack message
  @NonCPS
  def getTestSummary = { ->
    def testResultAction = currentBuild.rawBuild.getAction(AbstractTestResultAction.class)
    def summary = ""
    
    // If the unit tests fail to execute, no unit tests will be sent to Slack
    if (testResultAction != null) {
        def total = testResultAction.getTotalCount()
        def failed = testResultAction.getFailCount()
        def skipped = testResultAction.getSkipCount()
        def failedTests = testResultAction.getFailedTests()
        echo "These are the failed tests: ${failedTests}"
        
        // If the unit tests found a failed test result it will be included in the Slack message otherwise nah 
        if (failedTests.isEmpty() != true) {
          summary = "Test results:\n\t"
          summary = summary + ("Passed: " + (total - failed - skipped))
          summary = summary + (", Failed: " + failed + " ${testResultAction.failureDiffString}")
          summary = summary + (", Skipped: " + skipped)
          summary = summary + ("\nFailed tests:\n\t" + failedTests)
        } else {
          summary = "Test results:\n\t"
          summary = summary + ("Passed: " + (total - failed - skipped))
          summary = summary + (", Failed: " + failed + " ${testResultAction.failureDiffString}")
          summary = summary + (", Skipped: " + skipped)
        }
    } else {
        summary = "No tests found! :santas-not-happy:"
    }
    return summary
  }
 
  def testSummaryRaw = getTestSummary()
  // format test summary as a code block
  def testSummary = "${testSummaryRaw}"
  println testSummary.toString()

  JSONObject attachment = new JSONObject();
  attachment.put('author',"jenkins");
  attachment.put('author_link',"https://github.com/allanlim");
  attachment.put('title', title.toString());
  attachment.put('title_link',title_link.toString());
  attachment.put('text', subject.toString());
  attachment.put('fallback', "fallback message");
  attachment.put('color',colorCode);
  attachment.put('mrkdwn_in', ["fields"])
  // JSONObject for branch
  JSONObject branch = new JSONObject();
  branch.put('title', 'Branch:');
  branch.put('value', branchName.toString());
  branch.put('short', true);
  // JSONObject for author
  JSONObject commitAuthor = new JSONObject();
  commitAuthor.put('title', 'Author:');
  commitAuthor.put('value', author.toString());
  commitAuthor.put('short', true);
  // JSONObject for commit message
  JSONObject commitMessage = new JSONObject();
  commitMessage.put('title', pullTitle.toString());
  commitMessage.put('value', message.toString());
  commitMessage.put('short', false);
  // JSONObject for commits to pull request
  JSONObject allCommits = new JSONObject();
  allCommits.put('title', 'Changelog:');
  allCommits.put('value', commits.toString());
  allCommits.put('short', false);
  // JSONObject for test results
  JSONObject testResults = new JSONObject();
  testResults.put('title', 'Test Summary:')
  testResults.put('value', testSummary.toString())
  testResults.put('short', false)
  attachment.put('fields', [branch, commitAuthor, commitMessage, allCommits, testResults]);
  JSONArray attachments = new JSONArray();
  attachments.add(attachment);
  println attachments.toString()

  // Send notifications
  slackSend (color: colorCode, message: subject, attachments: attachments.toString(), channel: channel)  
  }

  // Iterates through the commit changelog between the remote and target repo's so it cab be sent in the Slack message
  @NonCPS
  def getChangeString() {
    
    def changeString = ""
    def changeLogSets = currentBuild.changeSets
    
    for (int i = 0; i < changeLogSets.size(); i++) {
        def entries = changeLogSets[i].items
        for (int j = 0; j < entries.length; j++) {
            def entry = entries[j]
            truncated_msg = entry.msg
            committer = entry.author
            //fullCommit = entry.commitId
            //commitLink = "https://github.com/ForcuraCo/forcura-coreapp/commit/${fullCommit}"
            commit = entry.commitId.take(7)
            
            // files = entry.file.editType.name
          //changeString += "<${commitLink}|${commit}> - ${truncated_msg} [${committer}]\n"
          changeString += "${commit} - ${truncated_msg} [${committer}]\n"
        }
    }

    if (!changeString) {
        changeString = "No new changes! :santas-not-happy:"
    }
    return changeString
  }
