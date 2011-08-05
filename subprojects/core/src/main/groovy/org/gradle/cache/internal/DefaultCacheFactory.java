/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.cache.internal;

import org.gradle.CacheUsage;
import org.gradle.api.Action;
import org.gradle.api.internal.Factory;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentStateCache;
import org.gradle.cache.Serializer;
import org.gradle.cache.internal.btree.BTreePersistentIndexedCache;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.util.*;

public class DefaultCacheFactory implements Factory<CacheFactory> {
    private final Map<File, DirCacheReference> dirCaches = new HashMap<File, DirCacheReference>();

    public CacheFactory create() {
        return new CacheFactoryImpl();
    }

    void onOpen(Object cache) {
    }

    void onClose(Object cache) {
    }

    private class CacheFactoryImpl implements CacheFactory {
        private final Collection<BasicCacheReference> caches = new HashSet<BasicCacheReference>();

        private DirCacheReference doOpenDir(File cacheDir, CacheUsage usage, Map<String, ?> properties, LockMode lockMode, Action<? super PersistentCache> action) {
            File canonicalDir = GFileUtils.canonicalise(cacheDir);
            DirCacheReference dirCacheReference = dirCaches.get(canonicalDir);
            if (dirCacheReference == null) {
                DefaultPersistentDirectoryCache cache = new DefaultPersistentDirectoryCache(canonicalDir, usage, properties, action);
                dirCacheReference = new DirCacheReference(cache, properties, lockMode);
                dirCaches.put(canonicalDir, dirCacheReference);
            } else {
                if (usage == CacheUsage.REBUILD && dirCacheReference.rebuiltBy != this) {
                    throw new IllegalStateException(String.format("Cannot rebuild cache '%s' as it is already open.", cacheDir));
                }
                if (lockMode != dirCacheReference.lockMode) {
                    throw new IllegalStateException(String.format("Cannot open cache '%s' with %s lock mode as it is already open with %s lock mode.", cacheDir, lockMode.toString().toLowerCase(), dirCacheReference.lockMode.toString().toLowerCase()));
                }
                if (!properties.equals(dirCacheReference.properties)) {
                    throw new IllegalStateException(String.format("Cache '%s' is already open with different state.", cacheDir));
                }
            }
            if (usage == CacheUsage.REBUILD) {
                dirCacheReference.rebuiltBy = this;
            }
            dirCacheReference.addReference(this);
            return dirCacheReference;
        }

        public PersistentCache open(File cacheDir, CacheUsage usage, Map<String, ?> properties, LockMode lockMode, Action<? super PersistentCache> initializer) {
            DirCacheReference dirCacheReference = doOpenDir(cacheDir, usage, properties, lockMode, initializer);
            return dirCacheReference.getCache();
        }

        public <E> PersistentStateCache<E> openStateCache(File cacheDir, CacheUsage usage, Map<String, ?> properties, LockMode lockMode, Serializer<E> serializer) {
            if (lockMode == LockMode.Shared) {
                throw new UnsupportedOperationException("No shared state cache implementation available.");
            }
            StateCacheReference<E> cacheReference = doOpenDir(cacheDir, usage, properties, LockMode.Exclusive, null).getStateCache(serializer);
            cacheReference.addReference(this);
            return cacheReference.getCache();
        }

        public <K, V> PersistentIndexedCache<K, V> openIndexedCache(File cacheDir, CacheUsage usage, Map<String, ?> properties, LockMode lockMode, Serializer<V> serializer) {
            if (lockMode == LockMode.Shared) {
                throw new UnsupportedOperationException("Not shared indexed cache implementation available.");
            }
            IndexedCacheReference<K, V> cacheReference = doOpenDir(cacheDir, usage, properties, LockMode.Exclusive, null).getIndexedCache(serializer);
            cacheReference.addReference(this);
            return cacheReference.getCache();
        }

        public void close() {
            try {
                for (BasicCacheReference cache : caches) {
                    cache.release(this);
                }
            } finally {
                caches.clear();
            }
        }
    }

    private abstract class BasicCacheReference<T> {
        private Set<CacheFactoryImpl> references = new HashSet<CacheFactoryImpl>();
        private final T cache;

        protected BasicCacheReference(T cache) {
            this.cache = cache;
            onOpen(cache);
        }

        public T getCache() {
            return cache;
        }

        public void release(CacheFactoryImpl owner) {
            boolean removed = references.remove(owner);
            assert removed;
            if (references.isEmpty()) {
                onClose(cache);
                close();
            }
        }

        public void addReference(CacheFactoryImpl owner) {
            references.add(owner);
            owner.caches.add(this);
        }

        public void close() {
        }
    }

    private class DirCacheReference extends BasicCacheReference<DefaultPersistentDirectoryCache> {
        private final Map<String, ?> properties;
        private final CacheFactory.LockMode lockMode;
        IndexedCacheReference indexedCache;
        StateCacheReference stateCache;
        CacheFactoryImpl rebuiltBy;

        public DirCacheReference(DefaultPersistentDirectoryCache cache, Map<String, ?> properties, CacheFactory.LockMode lockMode) {
            super(cache);
            this.properties = properties;
            this.lockMode = lockMode;
        }

        public <E> StateCacheReference<E> getStateCache(Serializer<E> serializer) {
            if (stateCache == null) {
                SimpleStateCache<E> stateCache = new SimpleStateCache<E>(getCache().getBaseDir(), serializer);
                this.stateCache = new StateCacheReference<E>(stateCache, this);
            }
            return stateCache;
        }

        public <K, V> IndexedCacheReference<K, V> getIndexedCache(Serializer<V> serializer) {
            if (indexedCache == null) {
                BTreePersistentIndexedCache<K, V> indexedCache = new BTreePersistentIndexedCache<K, V>(getCache().getBaseDir(), serializer);
                this.indexedCache = new IndexedCacheReference<K, V>(indexedCache, this);
            }
            return indexedCache;
        }

        public void close() {
            dirCaches.values().remove(this);
        }
    }

    private abstract class NestedCacheReference<T> extends BasicCacheReference<T> {
        protected final DefaultCacheFactory.DirCacheReference backingCache;

        protected NestedCacheReference(T cache, DirCacheReference backingCache) {
            super(cache);
            this.backingCache = backingCache;
        }
    }

    private class IndexedCacheReference<K, V> extends NestedCacheReference<BTreePersistentIndexedCache<K, V>> {
        private IndexedCacheReference(BTreePersistentIndexedCache<K, V> cache, DirCacheReference backingCache) {
            super(cache, backingCache);
        }

        @Override
        public void close() {
            super.close();
            backingCache.indexedCache = null;
            getCache().close();
        }
    }

    private class StateCacheReference<E> extends NestedCacheReference<SimpleStateCache<E>> {
        private StateCacheReference(SimpleStateCache<E> cache, DirCacheReference backingCache) {
            super(cache, backingCache);
        }

        @Override
        public void close() {
            super.close();
            backingCache.stateCache = null;
        }
    }
}