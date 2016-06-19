/*
 *  Copyright 2016 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.llvm;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.ServiceLoader;
import org.teavm.common.ServiceRepository;
import org.teavm.dependency.BootstrapMethodSubstitutor;
import org.teavm.dependency.DependencyChecker;
import org.teavm.dependency.DependencyInfo;
import org.teavm.dependency.DependencyListener;
import org.teavm.dependency.Linker;
import org.teavm.dependency.MethodDependency;
import org.teavm.diagnostics.AccumulationDiagnostics;
import org.teavm.diagnostics.ProblemProvider;
import org.teavm.javascript.spi.Generator;
import org.teavm.javascript.spi.Injector;
import org.teavm.llvm.context.LayoutRegistry;
import org.teavm.llvm.context.TagRegistry;
import org.teavm.llvm.context.VirtualTableRegistry;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ListableClassHolderSource;
import org.teavm.model.ListableClassReaderSource;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReference;
import org.teavm.model.MutableClassHolderSource;
import org.teavm.model.Program;
import org.teavm.model.ValueType;
import org.teavm.model.util.MissingItemsProcessor;
import org.teavm.model.util.ModelUtils;
import org.teavm.model.util.ProgramUtils;
import org.teavm.optimization.ArrayUnwrapMotion;
import org.teavm.optimization.ClassInitElimination;
import org.teavm.optimization.ConstantConditionElimination;
import org.teavm.optimization.Devirtualization;
import org.teavm.optimization.GlobalValueNumbering;
import org.teavm.optimization.Inlining;
import org.teavm.optimization.LoopInvariantMotion;
import org.teavm.optimization.LoopInversion;
import org.teavm.optimization.MethodOptimization;
import org.teavm.optimization.UnreachableBasicBlockElimination;
import org.teavm.optimization.UnusedVariableElimination;
import org.teavm.vm.TeaVMPluginLoader;
import org.teavm.vm.spi.RendererListener;
import org.teavm.vm.spi.TeaVMHost;
import org.teavm.vm.spi.TeaVMPlugin;

public class TeaVMLLVMEmitter implements TeaVMHost, ServiceRepository {
    private final ClassLoader classLoader;
    private final DependencyChecker dependencyChecker;
    private final AccumulationDiagnostics diagnostics = new AccumulationDiagnostics();
    private final Map<Class<?>, Object> services = new HashMap<>();
    private final Properties properties = new Properties();
    private String mainClassName;

    public TeaVMLLVMEmitter(ClassLoader classLoader, ClassReaderSource classSource) {
        this.classLoader = classLoader;
        dependencyChecker = new DependencyChecker(classSource, classLoader, this, diagnostics);
        dependencyChecker.addClassTransformer(new LLVMClassTransformer());
    }

    @Override
    public void add(DependencyListener dependencyListener) {
        dependencyChecker.addDependencyListener(dependencyListener);
    }

    @Override
    public void add(ClassHolderTransformer classTransformer) {
        dependencyChecker.addClassTransformer(classTransformer);
    }

    @Override
    public void add(MethodReference methodRef, Generator generator) {
    }

    @Override
    public void add(MethodReference methodRef, Injector injector) {
    }

    @Override
    public void add(MethodReference methodRef, BootstrapMethodSubstitutor substitutor) {
        dependencyChecker.addBootstrapMethodSubstitutor(methodRef, substitutor);
    }

    @Override
    public void add(RendererListener listener) {
    }

    @Override
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    @Override
    public Properties getProperties() {
        return new Properties(properties);
    }

    public void setProperties(Properties properties) {
        this.properties.clear();
        if (properties != null) {
            this.properties.putAll(properties);
        }
    }

    @Override
    public <T> T getService(Class<T> type) {
        Object service = services.get(type);
        if (service == null) {
            throw new IllegalArgumentException("Service not registered: " + type.getName());
        }
        return type.cast(service);
    }

    @Override
    public <T> void registerService(Class<T> type, T instance) {
        services.put(type, instance);
    }

    public ProblemProvider getDiagnostics() {
        return diagnostics;
    }

    public DependencyInfo getDependencyInfo() {
        return dependencyChecker;
    }

    /**
     * <p>Finds and install all plugins in the current class path. The standard {@link ServiceLoader}
     * approach is used to find plugins. So this method scans all
     * <code>META-INF/services/org.teavm.vm.spi.TeaVMPlugin</code> resources and
     * obtains all implementation classes that are enumerated there.</p>
     */
    public void installPlugins() {
        for (TeaVMPlugin plugin : TeaVMPluginLoader.load(classLoader)) {
            plugin.install(this);
        }
    }

    public void setMainClassName(String mainClassName) {
        this.mainClassName = mainClassName;
    }

    public void build(Appendable writer) throws IOException {
        MethodDependency mainMethod = dependencyChecker.linkMethod(new MethodReference(mainClassName, "main",
                ValueType.arrayOf(ValueType.object("java.lang.String")), ValueType.VOID), null);
        mainMethod.use();
        dependencyChecker.processDependencies();

        if (!diagnostics.getSevereProblems().isEmpty()) {
            return;
        }

        ListableClassHolderSource classSet = link(dependencyChecker);
        devirtualize(classSet, dependencyChecker);
        optimize(classSet);

        VirtualTableRegistry vtableRegistry = buildVirtualTables(classSet);
        LayoutRegistry layoutRegistry = buildLayouts(classSet);
        TagRegistry tagRegistry = new TagRegistry(classSet);
        LLVMRenderer renderer = new LLVMRenderer(classSet, vtableRegistry, layoutRegistry, tagRegistry, writer);
        renderer.renderPrologue();
        renderer.renderInterfaceTable();
        renderer.renderClasses(classSet.getClassNames());
        renderer.renderMain(mainMethod.getMethod().getReference());
        renderer.renderStringPool();
        renderer.renderEpilogue();
    }

    private ListableClassHolderSource link(DependencyInfo dependency) {
        Linker linker = new Linker();
        MutableClassHolderSource cutClasses = new MutableClassHolderSource();
        MissingItemsProcessor missingItemsProcessor = new MissingItemsProcessor(dependency, diagnostics);

        dependency.getReachableClasses().stream()
                .map(cls -> dependency.getClassSource().get(cls))
                .filter(Objects::nonNull)
                .forEach(clsReader -> {
                    ClassHolder cls = ModelUtils.copyClass(clsReader);
                    cutClasses.putClassHolder(cls);
                    missingItemsProcessor.processClass(cls);
                    linker.link(dependency, cls);
                });
        return cutClasses;
    }

    private void devirtualize(ListableClassHolderSource classes, DependencyInfo dependency)  {
        final Devirtualization devirtualization = new Devirtualization(dependency, classes);
        for (String className : classes.getClassNames()) {
            ClassHolder cls = classes.get(className);
            cls.getMethods().stream()
                    .filter(method -> method.getProgram() != null)
                    .forEach(devirtualization::apply);
        }

        final Inlining inlining = new Inlining();
        for (String className : classes.getClassNames()) {
            ClassHolder cls = classes.get(className);
            for (final MethodHolder method : cls.getMethods()) {
                if (method.getProgram() != null) {
                    inlining.apply(method.getProgram(), classes);
                }
            }
        }
    }

    private void optimize(ListableClassHolderSource classSource) {
        classSource.getClassNames().stream()
                .map(name -> classSource.get(name))
                .flatMap(cls -> cls.getMethods().stream())
                .forEach(method -> {
                    Program program = method.getProgram();
                    if (program == null || program.basicBlockCount() == 0) {
                        return;
                    }
                    program = ProgramUtils.copy(program);
                    for (MethodOptimization optimization : getOptimizations()) {
                        optimization.optimize(method, program);
                    }
                    method.setProgram(program);
                });
    }

    private List<MethodOptimization> getOptimizations() {
        return Arrays.asList(new ArrayUnwrapMotion(), new LoopInversion(), new LoopInvariantMotion(),
                new GlobalValueNumbering(), new ConstantConditionElimination(), new UnusedVariableElimination(),
                new ClassInitElimination(), new UnreachableBasicBlockElimination());
    }

    private VirtualTableRegistry buildVirtualTables(ListableClassReaderSource classSource) {
        VirtualTableRegistry registry = new VirtualTableRegistry(classSource);
        classSource.getClassNames().forEach(registry::fillClass);
        return registry;
    }

    private LayoutRegistry buildLayouts(ListableClassReaderSource classSource) {
        LayoutRegistry registry = new LayoutRegistry(classSource);
        classSource.getClassNames().forEach(registry::addClass);
        return registry;
    }
}
