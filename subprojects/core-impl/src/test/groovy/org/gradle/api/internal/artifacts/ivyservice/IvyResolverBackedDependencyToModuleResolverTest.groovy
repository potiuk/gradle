/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.artifacts.ivyservice

import org.apache.ivy.Ivy
import org.apache.ivy.core.module.descriptor.DependencyDescriptor
import org.apache.ivy.core.resolve.ResolveData
import org.apache.ivy.plugins.resolver.DependencyResolver
import org.apache.ivy.plugins.version.VersionMatcher
import spock.lang.Specification
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.apache.ivy.core.resolve.ResolvedModuleRevision
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.module.id.ModuleId
import org.apache.ivy.core.settings.IvySettings

class IvyResolverBackedDependencyToModuleResolverTest extends Specification {
    final DependencyResolver ivyResolver = Mock()
    final Ivy ivy = Mock()
    final ResolveData resolveData = Mock()
    final VersionMatcher versionMatcher = Mock()
    final IvyResolverBackedDependencyToModuleResolver resolver = new IvyResolverBackedDependencyToModuleResolver(ivy, resolveData, ivyResolver, versionMatcher)

    def setup() {
        IvySettings settings = Mock()
        _ * ivy.settings >> settings
    }

    def "resolves id for dependency with static version without resolving dependency"() {
        def dep = dependency()

        when:
        def state = resolver.create(dep)
        def id = state.id

        then:
        id == dep.dependencyRevisionId

        and:
        0 * ivyResolver._
    }

    def "resolves id for dependency with dynamic version by resolving dependency"() {
        def dep = dependency()
        def descriptor = module("2011")

        given:
        _ * versionMatcher.isDynamic(dep.dependencyRevisionId) >> true

        when:
        def state = resolver.create(dep)
        def id = state.id

        then:
        id == descriptor.moduleRevisionId

        and:
        1 * ivyResolver.getDependency(dep, resolveData) >> resolvedRevision(descriptor)
        0 * ivyResolver._
    }

    def "resolves descriptor for dependency by resolving dependency"() {
        def dep = dependency()
        def descriptor = module()

        when:
        def state = resolver.create(dep)
        def result = state.descriptor

        then:
        result == descriptor

        and:
        1 * ivyResolver.getDependency(dep, resolveData) >> resolvedRevision(descriptor)
        0 * ivyResolver._
    }

    def "caches descriptor"() {
        def dep = dependency()
        def descriptor = module()

        when:
        def state = resolver.create(dep)
        state.descriptor
        state.descriptor

        then:
        1 * ivyResolver.getDependency(dep, resolveData) >> resolvedRevision(descriptor)
        0 * ivyResolver._
    }

    def "caches descriptor for dependency with dynamic version"() {
        def dep = dependency()
        def descriptor = module()

        given:
        _ * versionMatcher.isDynamic(dep.dependencyRevisionId) >> true

        when:
        def state = resolver.create(dep)
        state.id
        state.descriptor

        then:
        1 * ivyResolver.getDependency(dep, resolveData) >> resolvedRevision(descriptor)
        0 * ivyResolver._
    }

    def "fails when dependency cannot be resolved"() {
        def dep = dependency()

        when:
        def state = resolver.create(dep)
        state.descriptor

        then:
        ModuleNotFoundException e1 = thrown()

        and:
        1 * ivyResolver.getDependency(dep, resolveData) >> null
        0 * ivyResolver._
    }

    def "wraps resolve failure"() {
        def failure = new RuntimeException()
        def dep = dependency()

        when:
        def state = resolver.create(dep)
        state.descriptor

        then:
        ModuleResolveException e1 = thrown()
        e1.cause == failure

        and:
        1 * ivyResolver.getDependency(dep, resolveData) >> {throw failure}
        0 * ivyResolver._
    }

    def "rethrows resolve failure"() {
        def failure = new RuntimeException()
        def dep = dependency()

        when:
        def state = resolver.create(dep)
        state.descriptor

        then:
        ModuleResolveException e1 = thrown()

        and:
        1 * ivyResolver.getDependency(dep, resolveData) >> {throw failure}
        0 * ivyResolver._

        when:
        state.descriptor

        then:
        ModuleResolveException e2 = thrown()
        e2 == e1

        and:
        0 * ivyResolver._
    }

    def dependency() {
        DependencyDescriptor descriptor = Mock()
        _ * descriptor.dependencyRevisionId >> new ModuleRevisionId(new ModuleId("group", "module"), "1.2")
        return descriptor
    }

    def module(String version = "1.2") {
        ModuleDescriptor descriptor = Mock()
        _ * descriptor.moduleRevisionId >> new ModuleRevisionId(new ModuleId("group", "module"), version)
        return descriptor
    }

    def resolvedRevision(ModuleDescriptor descriptor) {
        ResolvedModuleRevision resolved = Mock()
        _ * resolved.descriptor >> descriptor
        return resolved
    }
}
