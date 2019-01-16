package com.dubreuia.processors;

import com.dubreuia.core.ExecutionMode;
import com.dubreuia.core.component.SaveActionManager;
import com.dubreuia.model.Action;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static com.dubreuia.core.ExecutionMode.normal;
import static com.dubreuia.core.ExecutionMode.shortcut;
import static com.dubreuia.utils.Helper.toVirtualFiles;

/**
 * Available processors for build.
 */
public enum BuildProcessor implements Processor {

    compile(Action.compile,
            EnumSet.of(normal, shortcut),
            (project, psiFiles) -> () -> {
                if (SaveActionManager.getInstance().isCompilingAvailable()) {
                    // TODO this doesn't work in webstorm
                    CompilerManager.getInstance(project).compile(toVirtualFiles(psiFiles), null);
                }
            }),

    ;

    private final Action action;
    private final BiFunction<Project, PsiFile[], Runnable> command;
    private final Set<ExecutionMode> modes;

    BuildProcessor(Action action, Set<ExecutionMode> modes, BiFunction<Project, PsiFile[], Runnable> command) {
        this.action = action;
        this.command = command;
        this.modes = modes;
    }

    @Override
    public Action getAction() {
        return action;
    }

    @Override
    public Set<ExecutionMode> getModes() {
        return modes;
    }

    @Override
    public int getOrder() {
        return 2;
    }

    @Override
    public SaveCommand getSaveCommand(Project project, Set<PsiFile> psiFiles) {
        return new GenericCommand(project, psiFiles, getModes(), getAction(), command);
    }

    public BiFunction<Project, PsiFile[], Runnable> getCommand() {
        return command;
    }

    public static Optional<Processor> getProcessorForAction(Action action) {
        return stream().filter(processor -> processor.getAction().equals(action)).findFirst();
    }

    public static Stream<Processor> stream() {
        return Arrays.stream(values());
    }

}
