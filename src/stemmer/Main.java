package stemmer;

import stemmer.clustering.ClusterSet;
import stemmer.clustering.HierarchicalClustering;
import stemmer.clustering.HistoryClusterBuilder;
import stemmer.clustering.MergeHistoryRecord;
import stemmer.yass.Experiment;
import stemmer.yass.YASS;

import java.io.*;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

import opennlp.tools.stemmer.snowball.SnowballStemmer;

import org.languagetool.JLanguageTool;
import org.languagetool.language.Russian;
import org.languagetool.rules.Rule;
import org.languagetool.rules.RuleMatch;

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

    public static void main(String[] args) throws IOException {


        runPorter();

        runAndSaveYASS(args);
    }

    private static void spellChecker(List<String> words) throws FileNotFoundException {
        JLanguageTool langTool = new JLanguageTool(new Russian());
        for (Rule rule : langTool.getAllRules()) {
            if (!rule.isDictionaryBasedSpellingRule()) {
                langTool.disableRule(rule.getId());
            }
        }

    }
    private static List<String> loadLexicon(String filename, List<String> stopwords, long startTime) {
        System.out.println("Loading lexicon...");

        List<String> lexicon = new ArrayList<>();
        int discardedNumbers = 0;
        int discardedStopwords = 0;

        try {
            File fileDir = new File(filename);
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(
                            new FileInputStream(fileDir), "UTF8"));
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                line  = line.split(" ")[0];

                //If I have to discard the numbers, I reject the numbers exp.isDiscardNumbers() &&
                if (Character.isDigit(line.charAt(0))) {
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
        return lexicon;
    }
    private static void runPorter() throws IOException {
        SnowballStemmer stemmer = new SnowballStemmer(SnowballStemmer.ALGORITHM.RUSSIAN);

        DataInputStream in = null;
        BufferedReader br;
        String strLine;
        int countForTrueSnowball;
        int countForFalseSnowball;
        int countForTruePorter;
        int countForFalsePorter;
        FileInputStream fstream = new FileInputStream("lexicon/russian/DataSet.txt") ;
        in = new DataInputStream(fstream);

        br = new BufferedReader(new InputStreamReader(in));
        countForTrueSnowball = 0;
        countForFalseSnowball = 0;
        countForTruePorter = 0;
        countForFalsePorter = 0;
        int strcount = 0;
        while ((strLine = br.readLine()) != null) {
            if(strcount > 500) break;
            strcount ++;
            String[] delims = strLine.split(" ");
            String first = delims[0];
            System.out.println(String.format("First: %s, StemSnowball: %s, StemPorter:%s, Second: %s",
                    first, stemmer.stem(first), Porter.stem(first), delims[1]));
            if (stemmer.stem(first).equals(delims[1])) {
                countForTrueSnowball++;
            } else {
                countForFalseSnowball++;
            }
            if (Porter.stem(first).equals(delims[1])) {
                countForTruePorter++;
            } else {
                countForFalsePorter++;
            }
        }
        System.out.println(String.format("Snowball: True: %d, False: %d", countForTrueSnowball, countForFalseSnowball));
        System.out.println(String.format("Porter: True: %d, False: %d", countForTruePorter, countForFalsePorter));
        in.close();
    }
    private static void runAndSaveYASS(String args [])
    {
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
        List<String> lexicon = loadLexicon(exp.getLexiconPath(), stopwords, startTime);

        //reduce the lexicon
        if (exp.getLexiconRangeStart() != -1 && exp.getLexiconRangeEnd() != -1) {
            lexicon = lexicon.subList(exp.getLexiconRangeStart(), exp.getLexiconRangeEnd());
        }

        System.out.println("Perform the clustering algorithm with the measurement" + exp.getDistanceMeasure().getName());

        List<MergeHistoryRecord> mergeHistory = HierarchicalClustering.calculateClusters(exp.getDistanceMeasure(), lexicon);

        System.out.println("Completed clustering! Time passed: " + (System.currentTimeMillis() - startTime)/1000);

        List<ClusterSet> snapshots = HistoryClusterBuilder.buildSetsFromHistory(lexicon, mergeHistory, exp.getThresholds());

        for (ClusterSet cs : snapshots) {
            Map<String, String> stemmedDict = YASS.stemFromClusterSet(cs);
            saveStemmedDict(exp.getName(), stemmedDict, exp.getDistanceMeasure().getName(), cs.getThreshold());
        }

        System.out.println("Completion completed. Total duration: " + (System.currentTimeMillis() - startTime)/1000 + " s");
    }

}
