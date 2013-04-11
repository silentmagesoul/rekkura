package rekkura.model;

import java.util.List;
import java.util.Map;

import rekkura.util.Colut;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

public class Unification {
	public final ImmutableList<Dob> vars;
	
	private Dob[] bay;
	
	public Unification(Map<Dob, Dob> source, List<Dob> ordering) {
		this.vars = ImmutableList.copyOf(ordering);
		
		bay = new Dob[ordering.size()];
		for (int i = 0; i < ordering.size(); i++) {
			bay[i] = source.get(ordering.get(i));
		}
	}
	
	public Unification(List<Dob> vars) {
		this(EMPTY_MAP, vars);
	}

	/**
	 * This merge may alter the state of the current unification on failure.
	 * @param other
	 * @return
	 */
	public boolean dirtyMergeWith(Unification other) {
		if (other == null || other.bay.length != this.bay.length) return false;
		for (int i = 0; i < this.bay.length; i++) {
			if (this.bay[i] == null) this.bay[i] = other.bay[i];
			else if (other.bay[i] != null && this.bay[i] != other.bay[i]) return false;
		}
		return true;
	}
	
	public Map<Dob, Dob> toMap() {
		Map<Dob, Dob> result = Maps.newHashMap();
		for (int i = 0; i < vars.size(); i++) {
			if (bay[i] == null) continue;
			Dob var = vars.get(i);
			result.put(var, bay[i]);
		}
		return result;
	}
	
	public boolean isValid() { return Colut.noNulls(this.bay); }
	
	public static final ImmutableMap<Dob, Dob> EMPTY_MAP = ImmutableMap.of();
}