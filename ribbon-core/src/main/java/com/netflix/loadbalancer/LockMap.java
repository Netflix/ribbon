package com.netflix.loadbalancer;

// like synchronizedMap, but with support for explicit re-entrant
// locking

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

public class LockMap<K,V> extends HashMap<K,V> {
		protected ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();

		protected ReadLock rl  = rwl.readLock();
		protected WriteLock wl = rwl.writeLock();

		public LockMap() {
				super();
		}

		public LockMap(int initialCapacity) {
				super(initialCapacity);
		}

		public LockMap(Map<? extends K, ? extends V> m) {
				super(m);
		}

		public LockMap(int initialCapacity, float loadFactor) {
				super(initialCapacity, loadFactor);
		}

		public void readLock() {
				rl.lock();
		}

		public void readUnlock() {
				rl.unlock();
		}

		public void writeLock() {
				wl.lock();
		}

		public void writeUnlock() {
				wl.unlock();
		}



		@Override public void clear() {
				try {
						wl.lock();
						super.clear();
				} finally {
						wl.unlock();
				}
		}

		@Override public boolean containsKey(Object key) {
				try {
						rl.lock();
						return super.containsKey(key);
				} finally {
						rl.unlock();
				}
		}

		@Override public boolean containsValue(Object value) {
				try {
						rl.lock();
						return super.containsValue(value);
				} finally {
						rl.unlock();
				}
		}

		@Override public Set<Map.Entry<K,V>> entrySet() {
				try {
						rl.lock();
						return super.entrySet();
				} finally {
						rl.unlock();
				}
		}

		@Override public V get(Object key) {
				try {
						rl.lock();
						return super.get(key);
				} finally {
						rl.unlock();
				}
		}

		@Override public boolean isEmpty() {
				try {
						rl.lock();
						return super.isEmpty();
				} finally {
						rl.unlock();
				}
		}

		@Override public Set<K> keySet() {
				try {
						rl.lock();
						return super.keySet();
				} finally {
						rl.unlock();
				}
		}

		@Override public V put(K key, V value) {
				try {
						wl.lock();
						return super.put(key, value);
				} finally {
						wl.unlock();
				}
		}

		@Override public void putAll(Map<? extends K, ? extends V> m) {
				try {
						wl.lock();
						super.putAll(m);
				} finally {
						wl.unlock();
				}
		}

		@Override public V remove(Object key) {
				try {
						wl.lock();
						return super.remove(key);
				} finally {
						wl.unlock();
				}
		}

		@Override public int size() {
				try {
						rl.lock();
						return super.size();
				} finally {
						rl.unlock();
				}
		}

		@Override public Collection<V> values() {
				try {
						rl.lock();
						return super.values();
				} finally {
						rl.unlock();
				}
		}

		@Override public Object clone() {
				try {
						rl.lock();
						
						LockMap map = new LockMap();
						map.putAll(this);

						return map;
				} finally {
						rl.unlock();
				}
		}
}
