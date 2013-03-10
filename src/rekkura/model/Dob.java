package rekkura.model;

import java.util.Iterator;
import java.util.List;
import java.util.Stack;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * (Discrete Object) A dob represents a node with ordered 
 * children that may have a name attached.
 * @author ptpham
 *
 */
public class Dob {
	public final String name;
	private List<Dob> children;
	
	public Dob(String name) { 
		this.name = name;
		this.children = Lists.newArrayList();
	}
	
	public Dob(List<Dob> children) { 
		this.name = "";
		this.children = Lists.newArrayList(children);
	}

	public boolean isTerminal() { return this.children.size() == 0; }
	public Dob at(int pos) { return this.children.get(pos); }
	public int size() { return this.children.size(); }
	public List<Dob> childCopy() { return Lists.newArrayList(this.children); }

	public Iterator<Dob> fullIterator() {
		return new Iterator<Dob>() {
			Stack<Dob> unused = new Stack<Dob>();
			{ unused.push(Dob.this); }
			
			@Override public boolean hasNext() 
			{ return unused.size() > 0; }

			@Override
			public Dob next() {
				Dob result = unused.pop();
				Iterables.addAll(unused, result.childIterable());
				return result;
			}

			@Override public void remove() 
			{ throw new IllegalAccessError("Remove not allowed!"); }
		};
	}
	
	public Iterable<Dob> fullIterable() {
		return new Iterable<Dob>() {
			@Override public Iterator<Dob> iterator() {
				return Dob.this.fullIterator();
			}
		};
	}

	public Iterable<Dob> childIterable() { return this.children; }
	
	@Override public String toString() {
		if (isTerminal()) return super.toString() + "(" + this.name + ")";
		return super.toString() + "(" + this.children + ")";
	}
}
