/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020 Alexandre DuBreuil
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package com.dubreuia.core.listener;

import com.dubreuia.core.service.SaveActionsService;
import com.dubreuia.core.service.SaveActionsServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import java.util.Scanner;
import com.intellij.ide.DataManager;
import java.util.Collections;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.AnAction;
import java.nio.file.Files;
import java.nio.file.Path;
import com.intellij.openapi.ui.playback.commands.ActionCommand;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.Presentation;
import java.io.File;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.PsiFile;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import com.intellij.psi.util.PsiUtilBase;

import static com.dubreuia.core.ExecutionMode.saveAll;
import static com.dubreuia.model.Action.activate;

/**
 * FileDocumentManagerListener to catch save events. This listener is registered as ExtensionPoint.
 */
public class SaveActionsDocumentManagerListener implements FileDocumentManagerListener {

    private static final Logger LOGGER = Logger.getInstance(SaveActionsService.class);

    private final Project project;
    private final PsiDocumentManager psiDocumentManager;

    private static boolean setupEmacsChecker = false;

    public SaveActionsDocumentManagerListener(Project project) {
        this.project = project;
        psiDocumentManager = PsiDocumentManager.getInstance(project);

        if (!setupEmacsChecker) {
            setupEmacsChecker = true;
            
            Runnable toRun = new Runnable() {
                    public void run() {
                        File f = new File("/Users/Gira/emacs-intellij.txt");
                        if (f.exists()) {

                            String fileString = "";
                            try {
                                Path fileName = Path.of("/Users/Gira/emacs-intellij.txt");
                                fileString = Files.readString(fileName);
                            } catch (Exception e) {}
                            
                            f.delete();

                            VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(fileString);
                            FileEditorManager.getInstance(project).openTextEditor(
                                                                                  new OpenFileDescriptor(project,
                                                                                                         virtualFile
                                                                                                         ),
                                                                                  true // request focus to editor
                                                                                  );
                            
                            ActionManager am = ActionManager.getInstance();
                            final String id = "ReformatCode";
                            final AnAction action = am.getAction(id);
                            am.tryToExecute(action, ActionCommand.getInputEvent(id), null, ActionPlaces.UNKNOWN, true);

                            String saveAllId = "SaveAll";
                            final AnAction saveAllAction = am.getAction(saveAllId);
                            am.tryToExecute(saveAllAction, ActionCommand.getInputEvent(saveAllId), null, ActionPlaces.UNKNOWN, true);
                            
                            am.tryToExecute(action, ActionCommand.getInputEvent(id), null, ActionPlaces.UNKNOWN, true);

                            try {
                                ProcessBuilder pb = new ProcessBuilder("/usr/local/bin/emacsclient", "-e", "(dima-emacs-intellij-done)");
                            Process p = pb.start();
                                p.waitFor();
                            } catch (Exception e) {}

                            File f3 = new File("/Users/Gira/emacs-intellij-test3.txt");
                            try {
                                f3.createNewFile();
                            } catch (Exception e) {
                            }
                        }
                    }
                };
            EdtExecutorService.getScheduledExecutorInstance().scheduleWithFixedDelay(toRun, 1, 1, TimeUnit.SECONDS);
        }
    }

    @Override
    public void beforeAllDocumentsSaving() {
        LOGGER.debug("[+] Start SaveActionsDocumentManagerListener#beforeAllDocumentsSaving, " + project.getName());
        List<Document> unsavedDocuments = Arrays.asList(FileDocumentManager.getInstance().getUnsavedDocuments());
        if (!unsavedDocuments.isEmpty()) {
            LOGGER.debug(String.format("Locating psi files for %d documents: %s", unsavedDocuments.size(), unsavedDocuments));
            beforeDocumentsSaving(unsavedDocuments);
        }
        LOGGER.debug("End SaveActionsDocumentManagerListener#beforeAllDocumentsSaving");
    }

    public void beforeDocumentsSaving(List<Document> documents) {
        if (project.isDisposed()) {
            return;
        }
        Set<PsiFile> psiFiles = documents.stream()
                .map(psiDocumentManager::getPsiFile)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        SaveActionsServiceManager.getService().guardedProcessPsiFiles(project, psiFiles, activate, saveAll);
    }

}
