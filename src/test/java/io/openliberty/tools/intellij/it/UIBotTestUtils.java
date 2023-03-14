/*******************************************************************************
 * Copyright (c) 2023 IBM Corporation.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package io.openliberty.tools.intellij.it;

import com.intellij.remoterobot.RemoteRobot;
import com.intellij.remoterobot.fixtures.ComponentFixture;
import com.intellij.remoterobot.fixtures.JButtonFixture;
import com.intellij.remoterobot.fixtures.JTextFieldFixture;
import com.intellij.remoterobot.fixtures.dataExtractor.RemoteText;
import com.intellij.remoterobot.search.locators.Locator;
import com.intellij.remoterobot.utils.RepeatUtilsKt;
import com.intellij.remoterobot.utils.WaitForConditionTimeoutException;
import io.openliberty.tools.intellij.it.fixtures.DialogFixture;
import io.openliberty.tools.intellij.it.fixtures.ProjectFrameFixture;
import io.openliberty.tools.intellij.it.fixtures.WelcomeFrameFixture;
import org.junit.Assert;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.util.List;

import static com.intellij.remoterobot.search.locators.Locators.byXpath;
import static com.intellij.remoterobot.stepsProcessing.StepWorkerKt.step;

/**
 * UI helper function.
 */
public class UIBotTestUtils {

    public enum PrintTo {
        STDOUT, FILE
    }

    /**
     * Imports a project using the UI.
     *
     * @param remoteRobot The RemoteRobot instance.
     * @param projectPath The project's absolute path.
     */
    public static void importProject(RemoteRobot remoteRobot, String projectPath, String projectName) {
        step("Import Project", () -> {
            // Start the open project dialog.
            WelcomeFrameFixture welcomePage = remoteRobot.find(WelcomeFrameFixture.class, Duration.ofSeconds(10));
            ComponentFixture cf = welcomePage.getOpenProjectComponentFixture("Open");
            cf.click();

            // Specify the project's path. The text field is pre-populated by default.
            DialogFixture newProjectDialog = welcomePage.find(DialogFixture.class, DialogFixture.byTitle("Open File or Project"), Duration.ofSeconds(10));
            JTextFieldFixture textField = newProjectDialog.getBorderLessTextField();
            JButtonFixture okButton = newProjectDialog.getButton("OK");
            RepeatUtilsKt.waitFor(Duration.ofSeconds(10),
                    Duration.ofSeconds(1),
                    "Waiting for init text on text field",
                    "Init text in text field is empty",
                    okButton::isEnabled);

            textField.setText(projectPath);
            RepeatUtilsKt.waitFor(Duration.ofSeconds(10),
                    Duration.ofSeconds(1),
                    "Waiting for open project text box to be populated with set value",
                    "Open project text box was not populated with set value",
                    () -> textField.getText().equals(projectPath));

            ComponentFixture projectTree = newProjectDialog.getTree();
            RepeatUtilsKt.waitFor(Duration.ofSeconds(10),
                    Duration.ofSeconds(1),
                    "Waiting for project tree to show the set project",
                    "The project tree did not show the set project",
                    () -> projectTree.getData().hasText(projectName));

            // Click OK.
            JButtonFixture jbf = newProjectDialog.button("OK");
            RepeatUtilsKt.waitFor(Duration.ofSeconds(10),
                    Duration.ofSeconds(1),
                    "Waiting for OK button to be enabled",
                    "OK button was not enabled",
                    jbf::isEnabled);
            jbf.click();

            // Need a buffer here.
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Closes the project frame.
     *
     * @param remoteRobot The RemoteRobot instance.
     */
    public static void closeProjectFrame(RemoteRobot remoteRobot) {
        // Click on File on the Menu bar.
        ProjectFrameFixture projectFrame = remoteRobot.find(ProjectFrameFixture.class, Duration.ofMinutes(2));
        ComponentFixture fileMenuEntry = projectFrame.getActionMenu("File");
        fileMenuEntry.click();

        // Click on Close Project in the menu.
        ComponentFixture closeFixture = projectFrame.getActionMenuItem("Close Project");
        closeFixture.click();
    }

    /**
     * Runs a dashboard action using the drop-down tree view.
     *
     * @param remoteRobot The RemoteRobot instance.
     * @param action      The action to run
     */
    public static void runDashboardActionFromDropDownView(RemoteRobot remoteRobot, String action) {
        ProjectFrameFixture projectFrame = remoteRobot.find(ProjectFrameFixture.class, Duration.ofSeconds(10));

        // Click on the Liberty toolbar to give it focus.
        ComponentFixture dashboardBar = projectFrame.getBaseLabel("Liberty", "10");
        dashboardBar.click();

        // Check if theproject tree was expanded and the action is showing.
        ComponentFixture treeFixture = projectFrame.getTree("LibertyTree", action, "1");
        RepeatUtilsKt.waitFor(Duration.ofSeconds(10),
                Duration.ofSeconds(2),
                "Waiting for " + action + " in tree fixture to show and come into focus",
                "Action " + action + " in tree fixture is not showing or not in focus",
                treeFixture::isShowing);

        // Double-click on the action.
        List<RemoteText> rts = treeFixture.findAllText();
        for (RemoteText rt : rts) {
            if (action.equals(rt.getText())) {
                Exception error = null;
                for (int i = 0; i < 3; i++) {
                    try {
                        error = null;
                        rt.doubleClick();
                        break;
                    } catch (Exception e) {
                        // The content of the Liberty tool window dashboard may blink in and out of existence; therefore,
                        // causing errors. Retry if that is the case.
                        TestUtils.printTrace(TestUtils.TraceSevLevel.INFO, "Double click on dashboard drop down action failed (" + e.getMessage() + "). Retrying.");
                        TestUtils.sleepAndIgnoreException(1);
                        error = e;
                    }
                }

                // Report the last error if there is one.
                if (error != null) {
                    error.printStackTrace();
                }

                break;
            }
        }
    }

    /**
     * Waits for the specified project to appear in the dashboard.
     *
     * @param remoteRobot The RemoteRobot instance.
     * @param projectName The project name to wait for.
     */
    public static void validateDashboardItemIsShowing(RemoteRobot remoteRobot, String projectName) {
        ProjectFrameFixture projectFrame = remoteRobot.find(ProjectFrameFixture.class, Duration.ofSeconds(10));

        // There is an idexing start window between which the dashboard content may come and go. Try to handle it.
        try {
            projectFrame.getTree("LibertyTree", projectName, "1");
        } catch (Exception e) {
            // Do nothing.
        }

        // Wait a bit and re-try. Indexing is a long process right now.
        TestUtils.sleepAndIgnoreException(60);
        ComponentFixture treeFixture = projectFrame.getTree("LibertyTree", projectName, "4");
        RepeatUtilsKt.waitFor(Duration.ofSeconds(10),
                Duration.ofSeconds(2),
                "Waiting for Tree fixture to show",
                "Tree fixture is not showing",
                treeFixture::isShowing);
    }

    /**
     * Waits for the Welcome page, which is shown when the project frame is closed.
     *
     * @param remoteRobot The RemoteRobot instance.
     */
    public static void validateProjectFrameClosed(RemoteRobot remoteRobot) {
        remoteRobot.find(WelcomeFrameFixture.class, Duration.ofMinutes(2));
    }

    /**
     * Opens the Liberty Tools dashboard if it is not already open.
     *
     * @param remoteRobot The RemoteRobot instance.
     */
    public static void openDashboardView(RemoteRobot remoteRobot) {
        ProjectFrameFixture projectFrame = remoteRobot.find(ProjectFrameFixture.class, Duration.ofSeconds(10));
        try {
            projectFrame.getBaseLabel("Liberty", "5");
        } catch (WaitForConditionTimeoutException e) {
            // Dashboard view is closed. Open it.
            clickOnWindowPaneStripeButton(remoteRobot, "Liberty");
        }
    }

    /**
     * Closes the Liberty tools dashboard if it is not already closed.
     *
     * @param remoteRobot The RemoteRobot instance.
     */
    public static void closeDashboardView(RemoteRobot remoteRobot) {
        ProjectFrameFixture projectFrame = remoteRobot.find(ProjectFrameFixture.class, Duration.ofSeconds(10));
        try {
            projectFrame.getBaseLabel("Liberty", "2");
            clickOnWindowPaneStripeButton(remoteRobot, "Liberty");
        } catch (WaitForConditionTimeoutException e) {
            // Dashboard view is already closed. Nothing to do.
        }
    }

    /**
     * Opens the project tree view if it is not already opened.
     *
     * @param remoteRobot The RemoteRobot instance.
     */
    public static void openProjectView(RemoteRobot remoteRobot) {
        ProjectFrameFixture projectFrame = remoteRobot.find(ProjectFrameFixture.class, Duration.ofSeconds(10));
        try {
            projectFrame.getContentComboLabel("Project", "5");
        } catch (WaitForConditionTimeoutException e) {
            // Dashboard view is closed. Open it.
            clickOnWindowPaneStripeButton(remoteRobot, "Project");
        }
    }

    /**
     * Closes the project tree view if it is not already closed.
     *
     * @param remoteRobot The RemoteRobot instance.
     */
    public static void closeProjectView(RemoteRobot remoteRobot) {
        ProjectFrameFixture projectFrame = remoteRobot.find(ProjectFrameFixture.class, Duration.ofSeconds(10));
        try {
            projectFrame.getContentComboLabel("Project", "2");
            clickOnWindowPaneStripeButton(remoteRobot, "Project");
        } catch (WaitForConditionTimeoutException e) {
            // Project view is already closed. Nothing to do.
        }
    }

    /**
     * Clicks on the specified tool window pane stripe.
     *
     * @param remoteRobot      The RemoteRobot instance.
     * @param StripeButtonName The name of the window pane stripe button.
     */
    public static void clickOnWindowPaneStripeButton(RemoteRobot remoteRobot, String StripeButtonName) {
        ProjectFrameFixture projectFrame = remoteRobot.find(ProjectFrameFixture.class, Duration.ofSeconds(10));
        ComponentFixture wpStripe = projectFrame.getStripeButton(StripeButtonName);
        wpStripe.click();
    }

    /**
     * Clicks on the expand action button on the dashboard view.
     *
     * @param remoteRobot The RemoteRobot instance.
     */
    public static void expandDashboardProjectTree(RemoteRobot remoteRobot) {
        ProjectFrameFixture projectFrame = remoteRobot.find(ProjectFrameFixture.class, Duration.ofSeconds(10));

        // Click on the Liberty toolbar to give the dashboard view focus.
        ComponentFixture dashboardBar = projectFrame.getBaseLabel("Liberty", "10");
        dashboardBar.click();

        // Expand the project tree to show the available actions.
        Locator locator = byXpath("//div[@class='LibertyExplorer']//div[@class='ActionButton' and contains(@myaction.key, 'action.ExpandAll.text')]");
        ComponentFixture actionButton = projectFrame.getActionButton(locator);
        actionButton.click();
    }

    /**
     * Prints the UI Component hierarchy to the specified destination.
     *
     * @param printTo       The indicator that specifies where the output should go: FILE or STDOUT.
     * @param secondsToWait The seconds to wait before output is collected.
     */
    public static void printUIComponentHierarchy(PrintTo printTo, int secondsToWait) {
        try {
            if (secondsToWait > 0) {
                System.out.println("!!! MARKER: The output will be collected in " + secondsToWait + " seconds. !!!");
                Thread.sleep(secondsToWait * 1000L);
            }

            URL url = new URL(MavenSingleModAppTest.REMOTEBOT_URL);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");

            try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                try (FileWriter fw = new FileWriter("botCompHierarchy.html")) {
                    String inputLine;
                    while ((inputLine = br.readLine()) != null) {
                        switch (printTo) {
                            case STDOUT -> System.out.println(inputLine);
                            case FILE -> {
                                fw.write(inputLine);
                                fw.write("\n");
                            }
                            default -> Assert.fail("Invalid format to write : ");
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Failed to collect UI Component Hierarchy information: " + e.getCause());
        }
    }
}
