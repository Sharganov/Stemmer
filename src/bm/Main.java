package bm;

import bm.clustering.ClusterSet;
import bm.clustering.HierarchicalClustering;
import bm.clustering.HistoryClusterBuilder;
import bm.clustering.MergeHistoryRecord;
import bm.yass.Experiment;
import bm.yass.YASS;

import java.io.*;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class Main {

    private static final String D_OUTPUTS = "outputs";
    private static final String DN_STEMMED_DICT = "stemmed_dict";
    private static final String DN_HISTORIES = "histories";
    private static final String DN_DATA = "graph_data";


    private static void makeExperimentOutputDirs(String propertiesFilePath, String experimentName) {
        String experimentPath = D_OUTPUTS +"/" +experimentName+"/";
        String dictPath = experimentPath + DN_STEMMED_DICT;
        String historiesPath = experimentPath + DN_HISTORIES;
        String dataPath = experimentPath + DN_DATA;

        try {
            Files.createDirectories(Paths.get(experimentPath));
            Files.createDirectories(Paths.get(dictPath));
            Files.createDirectories(Paths.get(historiesPath));
            Files.createDirectories(Paths.get(dataPath));
            Files.copy(Paths.get(propertiesFilePath), Paths.get(experimentPath), StandardCopyOption.REPLACE_EXISTING);
        }
        catch (DirectoryNotEmptyException de) {
            System.out.println("Directory già esistenti.");
        }
        catch (Exception e) {
            System.err.println(e.toString());
        }
    }

    private static void saveStemmedDict(String expName, Map<String, String> dictionary, String distanceName, float threshold) {
        Object[] keys = dictionary.keySet().toArray();
        Arrays.sort(keys);
        String filePath = D_OUTPUTS + "/"+expName+"/"+DN_STEMMED_DICT+"/sd_"+distanceName+"_"+threshold+".dict";

        try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(filePath), "utf-8"))) {
            //  f.write("%s,%s,%s,%s\n" % (str(m.c1), str(m.c2), str(m.dist), str(m.cnt)))
            for (Object key: keys){
                String k = (String)key;
                writer.write(k +"\t" + dictionary.get(k));
                // Stando alle specifiche, l'ultima linea non deve avere il \n
                if (! k.equals(keys[keys.length-1])){
                    writer.write("\n");
                }
            }
        } catch (Exception e){
            System.err.println(e.getMessage());
        }
    }

    public static void main(String[] args) {

        int cores = Runtime.getRuntime().availableProcessors();
        System.out.println("Number of cores available: "+cores);

        String propertiesFilePath = args[1];
        System.out.println("Loading experiment from: "+ propertiesFilePath);

        // load algotithms settings
        Experiment exp = Experiment.loadFromFile(propertiesFilePath);

        makeExperimentOutputDirs(propertiesFilePath, exp.getName());

        // load stop words
        List<String> stopwords = new ArrayList<>();
        if (exp.getStopwordsPath() != null && !exp.getStopwordsPath().equals("")) {
            try {
                File fileDir = new File(exp.getStopwordsPath());
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(
                                new FileInputStream(fileDir), "UTF8"));
                String line;
                while ((line = in.readLine()) != null) {
                    stopwords.add(line);
                }
                in.close();
            } catch (Exception e) {
                System.err.println(e.getMessage());
            }
        }

        long startTime = System.currentTimeMillis();

        // Load lexicon
        System.out.println("Load lexicon...");

        List<String> lexicon = new ArrayList<>();
        int discardedNumbers = 0;
        int discardedStopwords = 0;

        try {
            File fileDir = new File(exp.getLexiconPath());
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(
                            new FileInputStream(fileDir), "UTF8"));
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (exp.isTerrierLexicon()) {
//
//                    If the lexicon is in the format
//                            // erzboel, term111208 Nt = 1 TF = 1 @ {0 3420690 3}
//                            // extract the word, otherwise use the line directly
                    line = line.split(",")[0];
                }
                //If I have to discard the numbers, I reject the numbers
                if (exp.isDiscardNumbers() && Character.isDigit(line.charAt(0))) {
                    discardedNumbers++;
                    continue;
                }

                //If  have to discard the stopwords, the gap
                if (stopwords.contains(line)){
                    discardedStopwords++;
                    continue;
                }
                lexicon.add(line);
            }
            in.close();
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
        System.out.println("Loaded lexicon of " + lexicon.size() + "terms. Time passed: " + (System.currentTimeMillis() - startTime)/1000 + " s");
        System.out.println("Discarded " + discardedNumbers + "numbers " + discardedStopwords +" stopwords");

        //reduce the lexicon
        if (exp.getLexiconRangeStart() != -1 && exp.getLexiconRangeEnd() != -1) {
            lexicon = lexicon.subList(exp.getLexiconRangeStart(), exp.getLexiconRangeEnd());
        }

        List<MergeHistoryRecord> mergeHistory = new ArrayList<>();

        System.out.println("perform the clustering algorithm with the measurement" + exp.getDistanceMeasure().getName());

        mergeHistory = HierarchicalClustering.calculateClusters(exp.getDistanceMeasure(), lexicon);

        System.out.println("Completed clustering! Time passed: " + (System.currentTimeMillis() - startTime)/1000);

        List<ClusterSet> snapshots = HistoryClusterBuilder.buildSetsFromHistory(lexicon, mergeHistory, exp.getThresholds());

        for (ClusterSet cs : snapshots) {
            Map<String, String> stemmedDict = YASS.stemFromClusterSet(cs);
            saveStemmedDict(exp.getName(), stemmedDict, exp.getDistanceMeasure().getName(), cs.getThreshold());
        }

        System.out.println("Completion completed. Total duration: " + (System.currentTimeMillis() - startTime)/1000 + " s");
    }
}
