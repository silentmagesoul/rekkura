package rekkura.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

import com.google.common.base.Function;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterators;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Multiset;
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

	public static <U> Multimap<U, U> joinRight(Iterable<Map<U, U>> path) {
		Multimap<U, U> result = null;
		for (Map<U, U> node : path) {
			Multimap<U, U> current = Multimaps.forMap(node);
			if (result == null) result = current;
			else result = OtmUtil.joinRight(result, current);
		}

		return result;
	}

	public static <U> Multimap<U, U> joinLeft(Iterable<Map<U, U>> path) {
		Multimap<U, U> result = null;
		for (Map<U, U> node : path) {
			Multimap<U, U> current = Multimaps.forMap(node);
			if (result == null) result = current;
			else result = OtmUtil.joinLeft(result, current);
		}

		return result;
	}

	public static <U, V> Multimap<V, V> joinBase(Map<U, V> first, Map<U, V> second) {
		Multimap<V, V> result = HashMultimap.create();

		for (Map.Entry<U, V> pair : first.entrySet()) {
			U key = pair.getKey();
			V secondValue = second.get(key);
			if (secondValue == null) continue;
			result.put(pair.getValue(), secondValue);
		}

		return result;
	}

	public static <U, V> Multimap<V, V> joinBase(Multimap<U, V> first, Multimap<U, V> second) {
		Multimap<V, V> result = HashMultimap.create();

		for (U u : first.keySet()) {
			Collection<V> firstValues = first.get(u);
			Collection<V> secondValues = second.get(u);
			if (secondValues == null) continue;
			for (V left : firstValues) for (V right : secondValues) result.put(left, right);
		}

		return result;
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

	public static <U, V> Multimap<U, V> getAll(Multimap<U, V> map, Iterable<U> keys) {
		Multimap<U, V> result = HashMultimap.create();
		if (keys == null) return result;
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

	public static <U, V> Map<U, V> randomAssignment(ListMultimap<U, V> actions, Random rand) {
		return randomAssignment(actions, null, rand);
	}

	public static <U, V> Map<U, V> randomAssignment(ListMultimap<U, V> actions, Map<U, V> fixed, Random rand) {
		Map<U, V> result = Maps.newHashMap();
		if (fixed != null) result.putAll(fixed);

		for (U key : actions.keySet()) {
			if (result.containsKey(key)) continue;
			V value = Colut.randomSelection(actions.get(key), rand);
			result.put(key, value);
		}

		return result;
	}

	public static <U, V> void retainAll(Collection<U> retain, Multimap<U, V> edges) {
		Iterator<U> iterator = edges.keySet().iterator();
		while (iterator.hasNext()) {
			if (retain.contains(iterator.next())) continue;
			iterator.remove();
		}
	}

	public static <U> Multimap<Integer, U> invertMultiset(Multiset<U> nodes) {
		Multimap<Integer, U> indexed = HashMultimap.create();
		for (U node : nodes.elementSet()) {
			int idx = nodes.count(node);
			indexed.put(idx, node);
		}
		return indexed;
	}

	public static <U,V> List<List<V>> select(Multimap<U,V> base, Iterable<U> selection) {
		List<List<V>> result = Lists.newArrayList();
		for (U u : selection) result.add(Lists.newArrayList(base.get(u)));
		return result;
	}

	public static <U,V> Multimap<V,U> invertMap(Map<U, V> raw) {
		if (raw == null) return null;
		Multimap<V,U> result = HashMultimap.create();
		Multimaps.invertFrom(Multimaps.forMap(raw), result);
		return result;
	}

	public static <U,V> Multimap<U,V> diffSelective(Collection<U> selected, 
			Multimap<U,V> current, Multimap<U,V> previous) {
		Multimap<U,V> diff = HashMultimap.create();
		for (U key : current.keySet()) {
			if (!selected.contains(key)) diff.putAll(key, current.get(key));
			else diff.putAll(key, Colut.difference(current.get(key), previous.get(key)));
		}
		return diff;
	}

	public static <U,V> Multimap<U,V> intersection(Multimap<U,V> first, Multimap<U,V> second) {
		Multimap<U,V> result = HashMultimap.create();
		for (U u : first.keySet()) {
			for (V v : first.get(u)) {
				if (second.containsEntry(u, v)) result.put(u,v);
			}
		}
		return result;
	}

	public static <U,V> boolean removeAll(Multimap<U,V> target, Multimap<U,V> remove) {
		boolean result = false;
		for (U u : remove.keySet()) {
			for (V v : remove.get(u)) {
				result |= target.remove(u, v);
			}
		}
		return result;
	}

	public static <U,V> Multimap<U,V> symmetricDifference(Multimap<U,V> first, Multimap<U,V> second) {
		Multimap<U,V> result = HashMultimap.create(), inter = intersection(first, second);
		result.putAll(first);
		result.putAll(second);
		removeAll(result, inter);
		return result;
	}

	public static <U,V> Multimap<String,String> stringify(Multimap<U,V> map) {
		Multimap<String,String> result = HashMultimap.create();
		for (U u : map.keySet()) for (V v : map.get(u)) result.put(u.toString(), v.toString());
		return result;
	}

	public static <U> Set<U> allNodes(Multimap<U,U> edges) {
		Set<U> result = Sets.newHashSet();
		result.addAll(edges.values());
		result.addAll(edges.keySet());
		return result;
	}

	public static <U, V> Multimap<V,V> replace(Multimap<U,U> original, Map<U,V> conversion) {
		Multimap<V,V> result = HashMultimap.create();
		for (U key : original.keySet()) {
			for (U value : original.get(key)) {
				V convKey = conversion.get(key);
				V convValue = conversion.get(value);
				if (convKey == null || convValue == null) continue;
				result.put(convKey, convValue);
			}
		}
		return result;
	}

	public static <U, V> void removeByValue(Multimap<U,V> map, Collection<V> remove) {
		Iterator<Map.Entry<U, V>> iterator = map.entries().iterator();
		while (iterator.hasNext()) if (remove.contains(iterator.next().getValue())) iterator.remove();
	}

	public static <U, V> void addAllEdges(Multimap<U,V> map, Iterable<U> keys, Iterable<V> values) {
		if (map == null || keys == null || values == null) return;
		for (U key : keys) for (V value : values) map.put(key, value);
	}

	public static <U, V> Map<U, V> randomAssignment(ListMultimap<U, V> actions) {
		return OtmUtil.randomAssignment(actions, new Random());
	}

	public static <U,V> Map<U,Integer> getNumValues(Multimap<U, V> map) {
		Map<U,Integer> result = Maps.newHashMap();
		for (U u : map.keySet()) result.put(u, map.get(u).size());
		return result;
	}

	public static <U,V> long cartesianSize(Multimap<U,V> map) {
		long result = 1;
		for (U key : map.keySet()) result *= map.get(key).size();
		return result;
	}

	public static <U, C extends Collection<U>> Multimap<U,C> indexByElems(Iterable<C> collections) {
		Multimap<U,C> result = HashMultimap.create();
		if (collections == null) return result;
		for (C c : collections) for (U u : c) result.put(u, c);
		return result;
	}
	
	public static <U, V> Multimap<V, V> transfer(Multimap<U,U> base, Multimap<U,V> prongs) {
		Multimap<V,V> result = HashMultimap.create();
		if (base == null || prongs == null) return result;
		for (Map.Entry<U, U> entry : base.entries()) {
			for (V first : prongs.get(entry.getKey())) {
				for (V second : prongs.get(entry.getValue())) {
					result.put(first, second);
				}
			}
		}
		return result;
	}
	
	public static <U, V> List<List<U>> getKeys(Iterable<? extends Multimap<U, V>> maps) {
		List<List<U>> keys = Lists.newArrayList();
		if (maps == null) return keys;
		for (Multimap<U,V> map : maps) keys.add(Lists.newArrayList(map.keySet()));
		return keys;
	}
}
