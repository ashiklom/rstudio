/*
 * GeneralPreferencesPane.java
 *
 * Copyright (C) 2009-17 by RStudio, Inc.
 *
 * Unless you have received this program directly from RStudio pursuant
 * to the terms of a commercial license agreement with RStudio, then
 * this program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 *
 */
package org.rstudio.studio.client.workbench.prefs.views;

import org.rstudio.core.client.BrowseCap;
import org.rstudio.core.client.StringUtil;
import org.rstudio.core.client.files.FileSystemContext;
import org.rstudio.core.client.prefs.PreferencesDialogBaseResources;
import org.rstudio.core.client.widget.DirectoryChooserTextBox;
import org.rstudio.core.client.widget.MessageDialog;
import org.rstudio.core.client.widget.SelectWidget;
import org.rstudio.core.client.widget.TextBoxWithButton;
import org.rstudio.studio.client.application.Desktop;
import org.rstudio.studio.client.application.model.RVersionSpec;
import org.rstudio.studio.client.application.model.RVersionsInfo;
import org.rstudio.studio.client.application.model.SaveAction;
import org.rstudio.studio.client.application.ui.RVersionSelectWidget;
import org.rstudio.studio.client.common.FileDialogs;
import org.rstudio.studio.client.common.GlobalDisplay;
import org.rstudio.studio.client.server.Server;
import org.rstudio.studio.client.server.ServerError;
import org.rstudio.studio.client.server.ServerRequestCallback;
import org.rstudio.studio.client.workbench.WorkbenchContext;
import org.rstudio.studio.client.workbench.model.RemoteFileSystemContext;
import org.rstudio.studio.client.workbench.model.Session;
import org.rstudio.studio.client.workbench.prefs.model.GeneralPrefs;
import org.rstudio.studio.client.workbench.prefs.model.HistoryPrefs;
import org.rstudio.studio.client.workbench.prefs.model.ProjectsPrefs;
import org.rstudio.studio.client.workbench.prefs.model.RPrefs;
import org.rstudio.studio.client.workbench.prefs.model.UIPrefs;
import org.rstudio.studio.client.workbench.views.terminal.TerminalShellInfo;

import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Label;
import com.google.inject.Inject;

public class GeneralPreferencesPane extends PreferencesPane
{
   @Inject
   public GeneralPreferencesPane(RemoteFileSystemContext fsContext,
                                 FileDialogs fileDialogs,
                                 UIPrefs prefs,
                                 Session session,
                                 final GlobalDisplay globalDisplay,
                                 final Server server,
                                 WorkbenchContext context)
   {
      fsContext_ = fsContext;
      fileDialogs_ = fileDialogs;
      prefs_ = prefs;
      session_ = session;
      server_ = server;
      
      RVersionsInfo versionsInfo = context.getRVersionsInfo();

      if (Desktop.isDesktop())
      {
         if (Desktop.getFrame().canChooseRVersion())
         {
            rVersion_ = new TextBoxWithButton(
                  "R version:",
                  "Change...",
                  new ClickHandler()
                  {
                     public void onClick(ClickEvent event)
                     {
                        String ver = Desktop.getFrame().chooseRVersion();
                        if (!StringUtil.isNullOrEmpty(ver))
                        {
                           rVersion_.setText(ver);

                           globalDisplay.showMessage(MessageDialog.INFO,
                                 "Change R Version",
                                 "You need to quit and re-open RStudio " +
                                 "in order for this change to take effect.");
                        }
                     }
                  });
            rVersion_.setWidth("100%");
            rVersion_.setText(Desktop.getFrame().getRVersion());
            spaced(rVersion_);
            add(rVersion_);
         }
      }
      else if (versionsInfo.isMultiVersion())
      {
         rServerRVersion_ = new RVersionSelectWidget(
                                       versionsInfo.getAvailableRVersions());
         add(tight(rServerRVersion_));
         
         rememberRVersionForProjects_ = 
                        new CheckBox("Restore last used R version for projects");
         
         rememberRVersionForProjects_.setValue(true);
         Style style = rememberRVersionForProjects_.getElement().getStyle();
         style.setMarginTop(5, Unit.PX);
         style.setMarginBottom(12, Unit.PX);
         add(rememberRVersionForProjects_);
      }

      Label defaultLabel = new Label("Default working directory (when not in a project):");
      nudgeRight(defaultLabel);
      add(tight(defaultLabel));
      add(dirChooser_ = new DirectoryChooserTextBox(null, 
                                                    null,
                                                    fileDialogs_, 
                                                    fsContext_));  
      spaced(dirChooser_);
      nudgeRight(dirChooser_);
      textBoxWithChooser(dirChooser_);

      showServerHomePage_ = new SelectWidget(
            "Show server home page:",
            new String[] {
                  "Multiple active sessions",
                  "Always",
                  "Never"
            },
            new String[] {
                 "sessions",
                 "always",
                 "never"
            },
            false,
            true,
            false);
      
      reuseSessionsForProjectLinks_ = new CheckBox("Re-use idle sessions for project links");
      
      if (session_.getSessionInfo().getShowUserHomePage())
      {
         spaced(showServerHomePage_);
         add(showServerHomePage_);
         lessSpaced(reuseSessionsForProjectLinks_);  
      }
      
      if (session_.getSessionInfo().getMultiSession())
         add(reuseSessionsForProjectLinks_);
      
      restoreLastProject_ = new CheckBox("Restore most recently opened project at startup");
      lessSpaced(restoreLastProject_);
      add(restoreLastProject_);
      
      add(checkboxPref("Restore previously open source documents at startup", prefs_.restoreSourceDocuments()));
        
      add(loadRData_ = new CheckBox("Restore .RData into workspace at startup"));
      lessSpaced(loadRData_); 
      
      saveWorkspace_ = new SelectWidget(
            "Save workspace to .RData on exit:",
            new String[] {
                  "Always",
                  "Never",
                  "Ask"
            });
      spaced(saveWorkspace_);
      add(saveWorkspace_);
      
      alwaysSaveHistory_ = new CheckBox(
            "Always save history (even when not saving .RData)");
      lessSpaced(alwaysSaveHistory_);
      add(alwaysSaveHistory_);
      
      removeHistoryDuplicates_ = new CheckBox(
                                 "Remove duplicate entries in history");
      spaced(removeHistoryDuplicates_);
      add(removeHistoryDuplicates_);

      showLastDotValue_ = new CheckBox("Show .Last.value in environment listing");
      lessSpaced(showLastDotValue_);
      add(showLastDotValue_);
      
      rProfileOnResume_ = new CheckBox("Run Rprofile when resuming suspended session");
      spaced(rProfileOnResume_);
      if (!Desktop.isDesktop())
         add(rProfileOnResume_);
           
      // The error handler features require source references; if this R
      // version doesn't support them, don't show these options. 
      if (session_.getSessionInfo().getHaveSrcrefAttribute())
      {
         add(checkboxPref(
               "Use debug error handler only when my code contains errors", 
               prefs_.handleErrorsInUserCodeOnly()));
         CheckBox chkTracebacks = checkboxPref(
               "Automatically expand tracebacks in error inspector", 
               prefs_.autoExpandErrorTracebacks());
         chkTracebacks.getElement().getStyle().setMarginBottom(15, Unit.PX);
         add(chkTracebacks);
      }
      
      CheckBox chkTabNavigation = checkboxPref(
            "Wrap around when navigating to previous/next tab",
            prefs_.wrapTabNavigation());
      spaced(chkTabNavigation);
      add(chkTabNavigation);
      
      // provide check for updates option in desktop mode when not
      // already globally disabled
      if (Desktop.isDesktop() && 
          !session.getSessionInfo().getDisableCheckForUpdates())
      {
         add(checkboxPref("Automatically notify me of updates to RStudio",
                          prefs_.checkForUpdates()));
      }
      
      if (session.getSessionInfo().getAllowShell() && haveTerminalShellPref())
      {
         terminalShell_ = new SelectWidget("Default terminal shell:");
         spaced(terminalShell_);
         add(terminalShell_);
         terminalShell_.setEnabled(false);
      }
      
      showServerHomePage_.setEnabled(false);
      reuseSessionsForProjectLinks_.setEnabled(false);
      saveWorkspace_.setEnabled(false);
      loadRData_.setEnabled(false);
      dirChooser_.setEnabled(false);
      alwaysSaveHistory_.setEnabled(false);
      removeHistoryDuplicates_.setEnabled(false);
      rProfileOnResume_.setEnabled(false);
      showLastDotValue_.setEnabled(false);
      restoreLastProject_.setEnabled(false);
   }
   
   @Override
   protected void initialize(RPrefs rPrefs)
   {
      // general prefs
      final GeneralPrefs generalPrefs = rPrefs.getGeneralPrefs();
      
      showServerHomePage_.setEnabled(true);
      reuseSessionsForProjectLinks_.setEnabled(true);
      saveWorkspace_.setEnabled(true);
      loadRData_.setEnabled(true);
      dirChooser_.setEnabled(true);
      
      showServerHomePage_.setValue(generalPrefs.getShowUserHomePage());
      reuseSessionsForProjectLinks_.setValue(generalPrefs.getReuseSessionsForProjectLinks());
      
      int saveWorkspaceIndex;
      switch (generalPrefs.getSaveAction())
      {
         case SaveAction.NOSAVE: 
            saveWorkspaceIndex = 1; 
            break;
         case SaveAction.SAVE: 
            saveWorkspaceIndex = 0; 
            break; 
         case SaveAction.SAVEASK:
         default: 
            saveWorkspaceIndex = 2; 
            break; 
      }
      saveWorkspace_.getListBox().setSelectedIndex(saveWorkspaceIndex);

      Scheduler.get().scheduleDeferred(new ScheduledCommand()
      {
         @Override
         public void execute()
         {
            server_.getTerminalShells(new ServerRequestCallback<JsArray<TerminalShellInfo>>()
            {
               @Override
               public void onResponseReceived(JsArray<TerminalShellInfo> shells)
               {
                  int currentShell = generalPrefs.getDefaultTerminalShellValue();
                  int currentShellIndex = 0;

                  GeneralPreferencesPane.this.terminalShell_.getListBox().clear();
                  
                  for (int i = 0; i < shells.length(); i++)
                  {
                     TerminalShellInfo info = shells.get(i);
                     GeneralPreferencesPane.this.terminalShell_.addChoice(
                           info.getShellName(), Integer.toString(info.getShellType()));
                     if (info.getShellType() == currentShell)
                        currentShellIndex = i;
                  }
                  if (GeneralPreferencesPane.this.terminalShell_.getListBox().getItemCount() > 0)
                  {
                     GeneralPreferencesPane.this.terminalShell_.setEnabled((true));
                     GeneralPreferencesPane.this.terminalShell_.getListBox().setSelectedIndex(currentShellIndex);
                  }
               }

               @Override
               public void onError(ServerError error) { }
            });
         }
      });

      loadRData_.setValue(generalPrefs.getLoadRData());
      dirChooser_.setText(generalPrefs.getInitialWorkingDirectory());
        
      // history prefs
      HistoryPrefs historyPrefs = rPrefs.getHistoryPrefs();
      
      alwaysSaveHistory_.setEnabled(true);
      removeHistoryDuplicates_.setEnabled(true);
      
      alwaysSaveHistory_.setValue(historyPrefs.getAlwaysSave());
      removeHistoryDuplicates_.setValue(historyPrefs.getRemoveDuplicates());
      
      rProfileOnResume_.setValue(generalPrefs.getRprofileOnResume());
      rProfileOnResume_.setEnabled(true);
      
      showLastDotValue_.setValue(generalPrefs.getShowLastDotValue());
      showLastDotValue_.setEnabled(true);
      
      if (rServerRVersion_ != null)
         rServerRVersion_.setRVersion(generalPrefs.getDefaultRVersion());
      
      if (rememberRVersionForProjects_ != null)
      {
         rememberRVersionForProjects_.setValue(
                                   generalPrefs.getRestoreProjectRVersion()); 
      }
     
      // projects prefs
      ProjectsPrefs projectsPrefs = rPrefs.getProjectsPrefs();
      restoreLastProject_.setEnabled(true);
      restoreLastProject_.setValue(projectsPrefs.getRestoreLastProject());
   }
   

   @Override
   public ImageResource getIcon()
   {
      return PreferencesDialogBaseResources.INSTANCE.iconR();
   }

   @Override
   public boolean onApply(RPrefs rPrefs)
   {
      boolean restartRequired = super.onApply(rPrefs);
 
      if (saveWorkspace_.isEnabled())
      {
         int saveAction;
         switch (saveWorkspace_.getListBox().getSelectedIndex())
         {
            case 0: 
               saveAction = SaveAction.SAVE; 
               break; 
            case 1: 
               saveAction = SaveAction.NOSAVE; 
               break; 
            case 2:
            default: 
               saveAction = SaveAction.SAVEASK; 
               break; 
         }
         
         int defaultShell = TerminalShellInfo.SHELL_DEFAULT;
         if (terminalShell_ != null && terminalShell_.isEnabled())
         {
            int idx = terminalShell_.getListBox().getSelectedIndex();
            String valStr = terminalShell_.getListBox().getValue(idx);
            defaultShell = StringUtil.parseInt(valStr, TerminalShellInfo.SHELL_DEFAULT);
         }

         // set general prefs
         GeneralPrefs generalPrefs = GeneralPrefs.create(showServerHomePage_.getValue(),
                                                         reuseSessionsForProjectLinks_.getValue(),
                                                         saveAction, 
                                                         loadRData_.getValue(),
                                                         rProfileOnResume_.getValue(),
                                                         dirChooser_.getText(),
                                                         getDefaultRVersion(),
                                                         getRestoreProjectRVersion(),
                                                         showLastDotValue_.getValue(),
                                                         defaultShell);
         rPrefs.setGeneralPrefs(generalPrefs);
         
         // set history prefs
         HistoryPrefs historyPrefs = HistoryPrefs.create(
                                          alwaysSaveHistory_.getValue(),
                                          removeHistoryDuplicates_.getValue());
         rPrefs.setHistoryPrefs(historyPrefs);
         
         
         // set projects prefs
         ProjectsPrefs projectsPrefs = ProjectsPrefs.create(
                                             restoreLastProject_.getValue());
         rPrefs.setProjectsPrefs(projectsPrefs);
      }

      return restartRequired;
   }

   @Override
   public String getName()
   {
      return "General";
   }

  
   
   private RVersionSpec getDefaultRVersion()
   {
      if (rServerRVersion_ != null)
         return rServerRVersion_.getRVersion();
      else
         return RVersionSpec.createEmpty();
   }
   
   private boolean getRestoreProjectRVersion()
   {
      if (rememberRVersionForProjects_ != null)
         return rememberRVersionForProjects_.getValue();
      else
         return false;
   }
   
   private boolean haveTerminalShellPref()
   {
      // TODO (gary) temporary pref to hide terminal feature by default on Windows;
      // remove this check when ready to turn on for Windows
      if (!BrowseCap.isWindowsDesktop() | !prefs_.enableXTerm().getValue())
         return false;
      
      return Desktop.isDesktop() && BrowseCap.isWindows();
   }

   private final FileSystemContext fsContext_;
   private final FileDialogs fileDialogs_;
   private RVersionSelectWidget rServerRVersion_ = null;
   private CheckBox rememberRVersionForProjects_ = null;
   private CheckBox reuseSessionsForProjectLinks_ = null;
   private SelectWidget showServerHomePage_;
   private SelectWidget saveWorkspace_;
   private TextBoxWithButton rVersion_;
   private TextBoxWithButton dirChooser_;
   private CheckBox loadRData_;
   private final CheckBox alwaysSaveHistory_;
   private final CheckBox removeHistoryDuplicates_;
   private CheckBox restoreLastProject_;
   private CheckBox rProfileOnResume_;
   private CheckBox showLastDotValue_;
   private SelectWidget terminalShell_;
   private final UIPrefs prefs_;
   private final Session session_;
   private final Server server_;
}
