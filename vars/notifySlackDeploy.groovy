#!/usr/bin/env groovy

/**
* notify slack and set message based on build status
*/

package hudson.tasks.junit;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import groovy.json.JsonOutput
import java.util.Optional
import hudson.tasks.test.AbstractTestResultAction;
import hudson.model.Actionable;



def call(String buildStatus = 'STARTED', String channel = '#engineering') {

  // buildStatus of null means success
  buildStatus = buildStatus ?: 'SUCCESS'
  channel = channel ?: '#engineering'

  // Default values
  def colorName = 'GREEN'
  def colorCode = '#2EB886'
  def subject = "${buildStatus} after ${currentBuild.durationString}"
  def title = "${env.JOB_NAME} - Build #${env.BUILD_NUMBER}"
  def title_link = "${env.RUN_DISPLAY_URL}"
  def branchName = "${GITHUB_BRANCH_NAME}"
  def application = getApplications()
  
  def commit = "${env.GIT_COMMIT}"
  def author = bat(script: "@echo off\ngit log -n 1 ${env.GIT_COMMIT} --format=%%aN", returnStdout: true).trim()
  //def committer = bat(script: "@echo off\ngit log -n 1 ${env.GIT_COMMIT} --format=%%cN", returnStdout: true).trim()
  def commits = getChangeString()
  
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
  } else if (buildStatus == 'FAILURE') {
    color = 'RED'
    colorCode = '#A30200'
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
        def failedTestsString = ""
        echo "These are the failed tests: ${failedTests}"
        //echo "These are the failed tests as a string: ${failedTestsString}"
        
        // If the unit tests found a failed test result it will be included in the Slack message otherwise nah 
        if (failedTests.isEmpty() != true) {
          for(CaseResult result : failedTests) {
            failedTestsString = failedTestsString + "- ${result.getFullDisplayName()}\n\t"
          }
          
          summary = "Test Results:\n\t"
          summary = summary + ("Passed: " + (total - failed - skipped))
          summary = summary + (", Failed: " + failed + " ${testResultAction.failureDiffString}")
          summary = summary + (", Skipped: " + skipped)
          summary = summary + ("\n" + failed + " Failed Tests:\n\t" + failedTestsString)
        } else {
          summary = "Test Results:\n\t"
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
  // JSONObject for application deployed
  JSONObject appDeployed = new JSONObject();
  appDeployed.put('title', 'Applications Deployed:');
  appDeployed.put('value', application.toString());
  appDeployed.put('short', false);
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
  attachment.put('fields', [branch, commitAuthor, appDeployed, testResults]);
  JSONArray attachments = new JSONArray();
  attachments.add(attachment);
  println attachments.toString()

  // Send notifications
  slackSend (color: colorCode, attachments: attachments.toString(), channel: channel)  
  }
  
  // Get the applications being deployed from the environmental variable in Jenkinsfile 
  @NonCPS 
  def getApplications () {
   
    def appString = "${APPS_DEPLOYED}"
    def applications = appString.split(',')
    def deployedApps = ""
    
    for (int i = 0; i < applications.size(); i++) {
         tempApp = applications[i]
         deployedApps += "• ${tempApp}\n"
     }
  return deployedApps
  }

  // Iterates through the commit changelog between the remote and target repo's so it can be sent in the Slack message
  @NonCPS
  def getChangeString() {
    
    def changeString = ""
    def changeLogSets = currentBuild.rawBuild.changeSets
    
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
