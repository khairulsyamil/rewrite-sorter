package me.khairulsyamil.openrewrite.java;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.internal.lang.NonNull;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.util.*;
import java.util.stream.Collectors;

public class JavaMethodSorter extends Recipe {
    @Override
    public @NonNull String getDisplayName() {
        return "JavaMethodSorter";
    }

    @Override
    public @NonNull String getDescription() {
        return "Reformat a java class or interface file to sort by name.";
    }

    @Override
    public String toString() {
        return "JavaMethodSorter{}";
    }

    @Override
    public @NonNull JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaMethodSorterVisitor();
    }

    public static class JavaMethodSorterVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public @NonNull J.ClassDeclaration visitClassDeclaration(@NonNull J.ClassDeclaration cd, @NonNull ExecutionContext ctx) {
            J.ClassDeclaration p = super.visitClassDeclaration(cd, ctx);

            p = sortMethods(p);

            return p;
        }

        private J.ClassDeclaration sortMethods(@NonNull J.ClassDeclaration cd) {
            J.Block block = cd.getBody();
            if (CollectionUtils.isEmpty(block.getStatements())) {
                return cd;
            }

            boolean anyUnsupportedStatements = block.getStatements().stream()
                    .anyMatch(s -> !((s instanceof J.EnumValueSet)
                            || (s instanceof J.VariableDeclarations)
                            || (s instanceof J.ClassDeclaration)
                            || (s instanceof J.MethodDeclaration)
                            || (s instanceof J.Block)));

            if (anyUnsupportedStatements) {
                throw new UnsupportedOperationException("Class contains an unsupported Statement type.");
            }

            List<Pair<UUID, J.Block>> blocks = new ArrayList<>();

            int idx = 0;
            for (Statement s: block.getStatements()) {
                if (s instanceof J.Block) {
                    if (idx > 0) {
                        blocks.add(Pair.of(block.getStatements().get(idx - 1).getId(), (J.Block) s));
                    }
                }

                idx++;
            }

            List<Statement> enumValueSets = block.getStatements().stream()
                    .filter(s -> s instanceof J.EnumValueSet)
                    .toList();

            List<Statement> sortedEnumValueSets = enumValueSets.stream()
                    .map(v -> {
                        J.EnumValueSet vd = (J.EnumValueSet) v;

                        vd.getEnums().sort((ae, be) -> StringUtils.compareIgnoreCase(ae.getName().getSimpleName(), be.getName().getSimpleName()));

                        return vd;
                    })
                    .collect(Collectors.toList());

            List<Statement> variables = block.getStatements().stream()
                    .filter(s -> s instanceof J.VariableDeclarations)
                    .toList();
            
            Set<String> variableNames = variables.stream()
                    .flatMap(v -> ((J.VariableDeclarations) v).getVariables().stream())
                    .map(v -> StringUtils.upperCase(v.getSimpleName()))
                    .collect(Collectors.toSet());

            List<Statement> sortedVariables = variables.stream()
                    .map(v -> {
                        J.VariableDeclarations vd = (J.VariableDeclarations) v;

                        vd.getVariables().sort((anv, bnv) -> StringUtils.compareIgnoreCase(anv.getSimpleName(), bnv.getSimpleName()));

                        return vd;
                    })
                    .sorted((avd, bvd) -> {
                        // Static first.
                        boolean aStatic = isStatic(avd);
                        boolean bStatic = isStatic(bvd);

                        if (aStatic != bStatic) {
                            return (aStatic) ? -1 : 1;
                        }

                        // Final next.
                        boolean aFinal = isFinal(avd);
                        boolean bFinal = isFinal(bvd);

                        if (aFinal != bFinal) {
                            return (aFinal) ? -1 : 1;
                        }

                        // High priority annotations.
                        int aAnnotations = hasHigherPriorityAnnotations(avd);
                        int bAnnotations = hasHigherPriorityAnnotations(bvd);

                        if (aAnnotations != bAnnotations) {
                            return (aAnnotations > bAnnotations) ? -1 : 1;
                        }

                        // Public, protected, private.
                        int aAccess = getAccessLevel(avd);
                        int bAccess = getAccessLevel(bvd);

                        if (aAccess != bAccess) {
                            return (aAccess > bAccess) ? -1 : 1;
                        }

                        String aname = avd.getVariables().stream()
                                .map(J.VariableDeclarations.NamedVariable::getSimpleName)
                                .collect(Collectors.joining(","));
                        String bname = bvd.getVariables().stream()
                                .map(J.VariableDeclarations.NamedVariable::getSimpleName)
                                .collect(Collectors.joining(","));

                        return StringUtils.compareIgnoreCase(aname, bname);
                    })
                    .collect(Collectors.toList());

            List<Statement> methods = block.getStatements().stream()
                    .filter(s -> s instanceof J.MethodDeclaration)
                    .toList();

            List<Statement> sortedMethods = methods.stream()
                    .sorted((a, b) -> {
                        J.MethodDeclaration amd = (J.MethodDeclaration) a;
                        J.MethodDeclaration bmd = (J.MethodDeclaration) b;

                        // Constructors first.
                        boolean isAConstructor = Objects.requireNonNull(amd.getMethodType()).isConstructor();
                        boolean isBConstructor = Objects.requireNonNull(bmd.getMethodType()).isConstructor();

                        if (isAConstructor != isBConstructor) {
                            if (isAConstructor) { return -1; }
                            return 1;
                        }

                        // High priority annotations.
                        int aAnnotations = hasHigherPriorityAnnotations(amd);
                        int bAnnotations = hasHigherPriorityAnnotations(bmd);

                        if (aAnnotations != bAnnotations) {
                            return (aAnnotations > bAnnotations) ? -1 : 1;
                        }

                        // Static first.
                        boolean aStatic = isStatic(amd);
                        boolean bStatic = isStatic(bmd);

                        if (aStatic != bStatic) {
                            return (aStatic) ? -1 : 1;
                        }

                        // Final next.
                        boolean aFinal = isFinal(amd);
                        boolean bFinal = isFinal(bmd);

                        if (aFinal != bFinal) {
                            return (aFinal) ? -1 : 1;
                        }

                        // Public, protected, private.
                        int aAccess = getAccessLevel(amd);
                        int bAccess = getAccessLevel(bmd);

                        if (aAccess != bAccess) {
                            return (aAccess > bAccess) ? -1 : 1;
                        }

                        // Compare name, with additional support for getters/setters to keep them together.
                        String aname = normalizeMethodNames(amd.getName().getSimpleName(), variableNames);
                        String bname = normalizeMethodNames(bmd.getName().getSimpleName(), variableNames);

                        return StringUtils.compareIgnoreCase(aname, bname);
                    })
                    .toList();

            List<Statement> classDeclarations = block.getStatements().stream()
                    .filter(s -> s instanceof J.ClassDeclaration)
                    .toList();

            List<Statement> sortedClassDeclarations = classDeclarations.stream()
                    .sorted((a, b) -> {
                        J.ClassDeclaration acd = (J.ClassDeclaration) a;
                        J.ClassDeclaration bcd = (J.ClassDeclaration) b;

                        // Compare name;
                        String aname = acd.getName().getSimpleName();
                        String bname = bcd.getName().getSimpleName();

                        return StringUtils.compareIgnoreCase(aname, bname);
                    })
                    .toList();

            List<Statement> combined = new ArrayList<>(
                    sortedEnumValueSets.size() +
                            sortedVariables.size() +
                            sortedMethods.size() +
                            sortedClassDeclarations.size() +
                            blocks.size());

            combined.addAll(sortedEnumValueSets);
            combined.addAll(sortedVariables);
            combined.addAll(sortedMethods);
            combined.addAll(sortedClassDeclarations);

            if (CollectionUtils.isNotEmpty(blocks)) {
                // Put back the static blocks immediately after the ids they were originally found after.
                for (Pair<UUID, J.Block> p: blocks) {
                    for (int i = 0; i < combined.size(); i++) {
                        if (combined.get(i).getId().equals(p.getKey())) {
                            combined.add(i + 1, p.getValue());
                            break;
                        }
                    }
                }
            }

            block = block.withStatements(combined);
            cd = cd.withBody(block);

            return cd;
        }

        private boolean isStatic (J.VariableDeclarations vd) {
            return vd.getModifiers().stream()
                    .anyMatch(m -> m.getType() == J.Modifier.Type.Static);
        }

        private boolean isFinal (J.VariableDeclarations vd) {
            return vd.getModifiers().stream()
                    .anyMatch(m -> m.getType() == J.Modifier.Type.Final);
        }

        private int getAccessLevel(J.VariableDeclarations vd) {
            if (isPrivate(vd)) {
                return 3;
            }

            if (isProtected(vd)) {
                return 2;
            }

            if (isPublic(vd)) {
                return 1;
            }

            return 0;
        }

        private boolean isPrivate (J.VariableDeclarations vd) {
            return vd.getModifiers().stream()
                    .anyMatch(m -> m.getType() == J.Modifier.Type.Private);
        }

        private boolean isProtected (J.VariableDeclarations vd) {
            return vd.getModifiers().stream()
                    .anyMatch(m -> m.getType() == J.Modifier.Type.Protected);
        }

        private boolean isPublic (J.VariableDeclarations vd) {
            return vd.getModifiers().stream()
                    .anyMatch(m -> m.getType() == J.Modifier.Type.Public);
        }

        private int hasHigherPriorityAnnotations(J.VariableDeclarations vd) {
            Set<String> annotations = vd.getLeadingAnnotations().stream()
                    .map(J.Annotation::getSimpleName)
                    .collect(Collectors.toSet());

            // JUnit 5
            if (CollectionUtils.containsAny(annotations,"Mock")) {
                return 100;
            }

            return 0;
        }

        private boolean isStatic (J.MethodDeclaration vd) {
            return vd.getModifiers().stream()
                    .anyMatch(m -> m.getType() == J.Modifier.Type.Static);
        }

        private boolean isFinal (J.MethodDeclaration vd) {
            return vd.getModifiers().stream()
                    .anyMatch(m -> m.getType() == J.Modifier.Type.Final);
        }

        private int getAccessLevel(J.MethodDeclaration vd) {
            if (isPrivate(vd)) {
                return 0;
            }

            if (isProtected(vd)) {
                return 50;
            }

            if (isPublic(vd)) {
                return 100;
            }

            return 100; // Default as public.
        }

        private boolean isPrivate (J.MethodDeclaration vd) {
            return vd.getModifiers().stream()
                    .anyMatch(m -> m.getType() == J.Modifier.Type.Private);
        }

        private boolean isProtected (J.MethodDeclaration vd) {
            return vd.getModifiers().stream()
                    .anyMatch(m -> m.getType() == J.Modifier.Type.Protected);
        }

        private boolean isPublic (J.MethodDeclaration vd) {
            return vd.getModifiers().stream()
                    .anyMatch(m -> m.getType() == J.Modifier.Type.Public);
        }
        
        private int hasHigherPriorityAnnotations(J.MethodDeclaration vd) {
            Set<String> annotations = vd.getLeadingAnnotations().stream()
                    .map(J.Annotation::getSimpleName)
                    .collect(Collectors.toSet());

            // JUnit 5
            if (CollectionUtils.containsAny(annotations,"Before", "BeforeClass", "BeforeEach", "BeforeAll")) {
                return 100;
            }
            if (CollectionUtils.containsAny(annotations,"After", "AfterClass", "AfterEach", "AfterAll")) {
                return 99;
            }
            
            return 0;
        }

        private String normalizeMethodNames(String name, Set<String> variableSet) {
            String upperName = StringUtils.upperCase(name);
            if (StringUtils.startsWithAny(upperName, "GET", "SET")) {
                String candidateVarName = upperName.substring(3);

                if (variableSet.contains(candidateVarName)) {
                    // Is a getter/setter.
                    // Normalize to variable name + G/S.
                    return candidateVarName + upperName.charAt(0);
                }
            }

            return name;
        }
    }
}
