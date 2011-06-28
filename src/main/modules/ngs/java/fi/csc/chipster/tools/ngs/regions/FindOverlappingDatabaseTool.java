package fi.csc.chipster.tools.ngs.regions;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

import fi.csc.chipster.tools.gbrowser.regions.RegionOperations;
import fi.csc.microarray.analyser.AnalysisDescription;
import fi.csc.microarray.analyser.java.JavaAnalysisHandler;
import fi.csc.microarray.client.visualisation.methods.gbrowser.message.RegionContent;

public class FindOverlappingDatabaseTool extends RegionTool {

	@Override
	public String getSADL() {
		return 	"TOOL FindOverlappingDatabaseTool.java: \"Find overlapping regions from database\" (Returns regions that have overlap with some region in a database.)" + "\n" +
				"INPUT data.bed: \"Set of regions\" TYPE GENERIC" + "\n" +
				"OUTPUT overlapping.bed: \"Overlapping regions\"" + "\n" + 
				"PARAMETER database: \"Database\" TYPE [miRBase16: \"miRBase 16\"] DEFAULT miRBase16 (Which database is used for comparison?)" + 
				"PARAMETER min.overlap.bp: \"Minimum number of overlapping basepairs\" TYPE INTEGER FROM 1 DEFAULT 1 (How many basepairs are required to consider regions overlapping?)";
	}

	@Override
	protected LinkedList<RegionContent> operate(LinkedList<List<RegionContent>> inputs, List<String> parameters) throws FileNotFoundException, IOException {

		// Add DB regions to inputs
		File dbDirectory = new File(((JavaAnalysisHandler)this.analysis.getHandler()).getParameters().get("externalToolPath"), "genomic_regions");
		List<RegionContent> dbRegions = new RegionOperations().loadFile(new File(dbDirectory, "miRBase16.bed"));
		inputs.add(dbRegions);
		
		LinkedList<String> newParameters = new LinkedList<String>();
		newParameters.add("first_augmented");
		newParameters.add(parameters.get(1)); // min.overlap.bp
		return new FindOverlappingTool().operate(inputs, newParameters);
	}
	
	public static void main(String[] args) throws Exception {
		// For testing
		String testPath = "/home/akallio/Desktop";  
		FindOverlappingDatabaseTool tool = new FindOverlappingDatabaseTool();
		HashMap<String, String> p = new LinkedHashMap<String, String>();
		p.put("externalToolPath", testPath);
		tool.analysis = new AnalysisDescription(new JavaAnalysisHandler(p), null);
		List<RegionContent> file1 = new RegionOperations().loadFile(new File(testPath, "miRNAseqHeight-preprocessed.bed"));
		LinkedList<List<RegionContent>> list = new LinkedList<List<RegionContent>>();
		list.add(file1);
		LinkedList<String> parameters = new LinkedList<String>();
		parameters.add("");
		parameters.add("1");
		new RegionOperations().print(tool.operate(list, parameters), System.out);
	}
}