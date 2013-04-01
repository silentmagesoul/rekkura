package rekkura.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import com.google.common.base.Function;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterators;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;

/**
 * (One-to-Many Utilities)
 * @author ptpham
 *
 */
public class OtmUtil {
	
	public static <U, V> Iterator<V> valueIterator(final Multimap<U, V> map, Iterator<U> keys) {
		if (map == null) return Iterators.emptyIterator();
		return new NestedIterator<U, V>(keys) {
			@Override protected Iterator<V> prepareNext(U u) {
				return map.get(u).iterator();
			}
		};
	}
	
	public static <U, V> Iterable<V> valueIterable(final Multimap<U, V> map, final Iterable<U> keys) {
		return new Iterable<V>() {
			@Override public Iterator<V> iterator() {
				return valueIterator(map, keys.iterator());
			}
		};
	}
	
	public static <U> Set<U> flood(Multimap<U, U> deps, U root) {
		Set<U> result = Sets.newHashSet();
		return flood(deps, root, result);
	}

	/**
	 * This method will not expand any visited nodes. It will return the 
	 * same set that was passed in.
	 * @param deps
	 * @param root
	 * @param visited
	 * @return
	 */
	public static <U> Set<U> flood(Multimap<U, U> deps, U root, Set<U> visited) {
		Queue<U> remaining = Queues.newLinkedBlockingQueue();
		remaining.add(root);
		
		while (!remaining.isEmpty()) {
			U next = remaining.poll();
			if (!visited.add(next)) continue;
			remaining.addAll(deps.get(next));
		}
		
		return visited;
	}
	
	public static <U> Set<U> flood(Multimap<U, U> deps, Collection<U> roots, Set<U> visited) {
		for (U u : roots) flood(deps, u, visited);
		return visited;
	}
	
	public static <U> Set<U> flood(Multimap<U, U> deps, Collection<U> roots) {
		return flood(deps, roots, Sets.<U>newHashSet());
	}
	
	public static <T, U, V> Multimap<U, T> expandRight(Multimap<U, V> map, Function<V, Collection<T>> fn) {
		Multimap<U, T> result = HashMultimap.create();
		
		for (U u : map.keySet()) {
			Set<T> values = Sets.newHashSet();
			for (V v : map.get(u)) {
				Collection<T> expansion = fn.apply(v);
				if (expansion == null) continue;
				values.addAll(expansion); 
			}
			if (Colut.empty(values)) continue;
			result.putAll(u, values);
		}
		
		return result;
	}

	public static <T, U, V> Multimap<T, V> expandLeft(Multimap<U, V> map, Function<U, Collection<T>> fn) {
		if (map == null) return null;
		Multimap<T, V> result = HashMultimap.create();
		
		for (U u : map.keySet()) {
			Collection<V> values = map.get(u);
			if (Colut.empty(values)) continue;
			Collection<T> col = fn.apply(u);
			if (col == null) continue;
			for (T t : col) { result.putAll(t, values); }
		}
		
		return result;
	}
	
	public static <T, U, V> Multimap<U, T> joinRight(Map<U, V> map, Map<V, T> other) {
		return joinRight(Multimaps.forMap(map), Multimaps.forMap(other));
	}
	
	public static <T, U, V> Multimap<U, T> joinRight(Multimap<U, V> map, final Multimap<V, T> other) {
		if (map == null) return null;
		Function<V, Collection<T>> joiner = new Function<V, Collection<T>>() {
			@Override public Collection<T> apply(V v) { return other.get(v); }
		};
		
		return expandRight(map, joiner);
	}

	public static <T, U, V> Multimap<T, V> joinLeft(Map<U, V> map, Map<U, T> other) {
		return joinLeft(Multimaps.forMap(map), Multimaps.forMap(other));
	}
	
	public static <T, U, V> Multimap<T, V> joinLeft(Multimap<U, V> map, final Multimap<U, T> other) {
		if (map == null) return null;
		
		Function<U, Collection<T>> joiner = new Function<U, Collection<T>>() {
			@Override public Collection<T> apply(U u) { return other.get(u); }
		};
		
		return expandLeft(map, joiner);
	}
	
	public static <U, V> Multimap<U, V> squashRight(Map<U, V> map, Map<V, V> other) {
		Multimap<U, V> result = joinRight(Multimaps.forMap(map), Multimaps.forMap(other));
		for (U u : map.keySet()) {
			if (!result.containsKey(u)) result.put(u, map.get(u));
		}
		return result;
	}
	
	public static <U, V> Multimap<U, V> squashLeft(Map<U, V> map, Map<U, U> other) {
		Multimap<U, V> result = joinLeft(Multimaps.forMap(map), Multimaps.forMap(other));
		for (U u : map.keySet()) {
			if (!other.containsKey(u)) result.put(u, map.get(u));
		}
		return result;
	}
	
	public static <U, V> Multimap<U, V> getAll(Multimap<U, V> map, Collection<U> keys) {
		Multimap<U, V> result = HashMultimap.create();
		for (U key : keys) result.putAll(key, map.get(key));
		return result;
	}
	
	public static <U, V> void putAll(Multimap<U, V> target, Map<U, V> map) {
		for (U u : map.keySet()) target.put(u, map.get(u));
	}
	
	public static <U, V> Multimap<U, V> flatten(Multimap<U, Collection<V>> map) {
		Multimap<U, V> result = HashMultimap.create();
		for (Map.Entry<U, Collection<V>> entry : map.entries()) {
			result.putAll(entry.getKey(), entry.getValue());
		}
		return result;
	}
}