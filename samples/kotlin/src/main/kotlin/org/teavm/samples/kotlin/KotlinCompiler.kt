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

package org.teavm.samples.kotlin

import com.intellij.codeInsight.ContainerProvider
import com.intellij.core.CoreApplicationEnvironment
import com.intellij.core.CoreProjectEnvironment
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.FileContextProvider
import com.intellij.psi.PsiElementFinder
import com.intellij.psi.PsiManager
import com.intellij.psi.augment.PsiAugmentProvider
import com.intellij.psi.augment.TypeAnnotationModifier
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.GroupingMessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.js.analyze.TopDownAnalyzerFacadeForJS
import org.jetbrains.kotlin.js.config.EcmaVersion
import org.jetbrains.kotlin.js.config.JSConfigurationKeys
import org.jetbrains.kotlin.js.config.LibrarySourcesConfig
import org.jetbrains.kotlin.parsing.KotlinParserDefinition
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.script.KotlinScriptDefinitionProvider
import org.jetbrains.kotlin.serialization.js.ModuleKind

fun main(args: Array<String>) {
    val collector = MessageCollectorImpl
    val groupingCollector = GroupingMessageCollector(collector)

    val configuration = CompilerConfiguration()
    configuration.put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, groupingCollector)
    configuration.put(JSConfigurationKeys.TARGET, EcmaVersion.defaultVersion())
    configuration.put(JSConfigurationKeys.MODULE_KIND, ModuleKind.UMD)
    configuration.put(CommonConfigurationKeys.MODULE_NAME, "out")

    val rootDisposable = Disposer.newDisposable()
    val environment = createEnvironment(rootDisposable)
    val project = environment.project

    val sourceFiles = getSourceFiles(project)
    val config = LibrarySourcesConfig(project, configuration)
    val analysisResult = TopDownAnalyzerFacadeForJS.analyzeFiles(sourceFiles, config)

    println(analysisResult)
}

private fun createEnvironment(disposable: Disposable): CoreProjectEnvironment {
    Extensions.cleanRootArea(disposable)

    //CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), BinaryFileStubBuilders.EP_NAME, FileTypeExtensionPoint::class.java)
    CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), FileContextProvider.EP_NAME, FileContextProvider::class.java)
    //
    CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), com.intellij.psi.meta.MetaDataContributor.EP_NAME, com.intellij.psi.meta.MetaDataContributor::class.java)
    CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), PsiAugmentProvider.EP_NAME, PsiAugmentProvider::class.java)
    //CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), JavaMainMethodProvider.EP_NAME, JavaMainMethodProvider::class.java)
    //
    CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), ContainerProvider.EP_NAME, ContainerProvider::class.java)
    //CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), ClsCustomNavigationPolicy.EP_NAME, ClsCustomNavigationPolicy::class.java)
    //CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), ClassFileDecompilers.EP_NAME, ClassFileDecompilers.Decompiler::class.java)
    //
    CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), TypeAnnotationModifier.EP_NAME, TypeAnnotationModifier::class.java)
    CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), com.intellij.psi.impl.PsiTreeChangePreprocessor.EP_NAME, com.intellij.psi.impl.PsiTreeChangePreprocessor::class.java)

    val appEnvironment = CoreApplicationEnvironment(disposable)

    appEnvironment.registerFileType(KotlinFileType.INSTANCE, "kt")
    appEnvironment.registerFileType(KotlinFileType.INSTANCE, KotlinParserDefinition.STD_SCRIPT_SUFFIX)
    appEnvironment.registerParserDefinition(KotlinParserDefinition())

    val projectEnv = object : CoreProjectEnvironment(disposable, appEnvironment) {
        override fun preregisterServices() {
            val area = Extensions.getArea(project)
            CoreApplicationEnvironment.registerExtensionPoint(area, com.intellij.psi.impl.PsiTreeChangePreprocessor.EP_NAME, com.intellij.psi.impl.PsiTreeChangePreprocessor::class.java)
            CoreApplicationEnvironment.registerExtensionPoint(area, PsiElementFinder.EP_NAME, PsiElementFinder::class.java)
        }
    }

    val kotlinScriptDefinitionProvider = KotlinScriptDefinitionProvider()
    projectEnv.project.registerService(KotlinScriptDefinitionProvider::class.java, kotlinScriptDefinitionProvider)
    /*projectEnv.project.registerService(KotlinScriptExternalImportsProvider::class.java,
            KotlinScriptExternalImportsProvider(projectEnv.project, kotlinScriptDefinitionProvider))*/

    return projectEnv
}

private fun getSourceFiles(project: Project): List<KtFile> {
    val files = mutableListOf<KtFile>()
    val vfs = VirtualFileManager.getInstance().getFileSystem(StandardFileSystems.FILE_PROTOCOL)
    val virtualFile = vfs.findFileByPath("input.kt")
    if (virtualFile != null) {
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
        if (psiFile is KtFile) {
            files += psiFile
        }
    }
    return files
}

private object MessageCollectorImpl : MessageCollector {
    private var errors = false

    override fun clear() {
        errors = false
    }

    override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageLocation) {
        if (severity.isError) {
            errors = true
        }
        println("at $location [${severity.name}] $message")
    }

    override fun hasErrors() = errors
}