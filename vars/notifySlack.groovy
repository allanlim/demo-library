#!/usr/bin/env groovy

/**
* notify slack and set message based on build status
*/
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.model.Actionable;

def call(String buildStatus = 'STARTED', String channel = '#engineering') {

  // buildStatus of null means successfull
  buildStatus = buildStatus ?: 'SUCCESSFUL'
  channel = channel ?: '#engineering'

  // 

  // Default values
  def colorName = 'GREEN'
  def colorCode = '#2EB886'
  def title = "${env.JOB_NAME} Build: ${env.BUILD_NUMBER}"
  def title_link = "${env.RUN_DISPLAY_URL}"
  def branchName = "${GIT_BRANCH}"

  def commit = "${GIT_COMMIT}"
  def author = "${GIT_AUTHOR}"
  def message = bat(returnStdout: true, script: 'git log --pretty=format:"%h - %an, %ar : %s"').trim()

  // Override default values based on build status
  if (buildStatus == 'STARTED') {
    color = 'GREEN'
    colorCode = '#2EB886'
  } else if (buildStatus == 'SUCCESSFUL') {
    color = 'GREEN'
    colorCode = '#2EB886'
  } else if (buildStatus == 'UNSTABLE') {
    color = 'YELLOW'
    colorCode = '#DAA038'
  } else {
    color = 'RED'
    colorCode = '#A30200'
  }
  
  // get test results for slack message
  @NonCPS
  def getTestSummary = { ->
    def testResultAction = currentBuild.rawBuild.getAction(AbstractTestResultAction.class)
    def summary = ""

    if (testResultAction != null) {
        def total = testResultAction.getTotalCount()
        def failed = testResultAction.getFailCount()
        def skipped = testResultAction.getSkipCount()

        summary = "Test results:\n\t"
        summary = summary + ("Passed: " + (total - failed - skipped))
        summary = summary + (", Failed: " + failed + " ${testResultAction.failureDiffString}")
        summary = summary + (", Skipped: " + skipped)
    } else {
        summary = "No tests found"
    }
    return summary
  }
  def testSummaryRaw = getTestSummary()
  // format test summary as a code block
  def testSummary = "`${testSummaryRaw}`"
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
  branch.put('title', 'Branch');
  branch.put('value', branchName.toString());
  branch.put('short', true);
  // JSONObject for author
  JSONObject commitAuthor = new JSONObject();
  commitAuthor.put('title', 'Author');
  commitAuthor.put('value', author.toString());
  commitAuthor.put('short', true);
  // JSONObject for branch
  JSONObject commitMessage = new JSONObject();
  commitMessage.put('title', 'Commit Message');
  commitMessage.put('value', message.toString());
  commitMessage.put('short', false);
  // JSONObject for test results
  JSONObject testResults = new JSONObject();
  testResults.put('title', 'Test Summary')
  testResults.put('value', testSummary.toString())
  testResults.put('short', false)
  attachment.put('fields', [branch, commitAuthor, commitMessage, testResults]);
  JSONArray attachments = new JSONArray();
  attachments.add(attachment);
  println attachments.toString()

  // Send notifications
  slackSend (color: colorCode, message: subject, attachments: attachments.toString(), channel: channel)

}
