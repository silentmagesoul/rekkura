package rekkura.test.logic.algorithm;

import rekkura.logic.algorithm.Renderer;

public class PartitioningTest extends ExpansionTest {
	@Override protected Renderer getExpansion() {
		Renderer.Partitioning result = Renderer.getPartitioning();
		result.minNonTrival = 1;
		return result;
	}
}