package stemmer.clustering;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Construct class reconstructs various sets of clusters from an operation history
 * merge of the hierarchical clustering algorithm.
 * */
public class HistoryClusterBuilder {

    public static List<ClusterSet> buildSetsFromHistory(List<String> words, List<MergeHistoryRecord> history, float[] thresholds){

        List<ClusterSet> snapshots = new ArrayList<>();

        ClusterSet clusterSet = new ClusterSet(0);

        // Reconstructs the initial status of the clustering algorithm
        for (int i = 0; i < words.size(); i++) {
            List<String> cw = new ArrayList<>();
            cw.add(words.get(i));
            clusterSet.addCluster(new Cluster(i, cw));
        }
        int nextId = words.size();

        // Sort the thresholds in ascending order
        Arrays.sort(thresholds);
        int cntThreshold = 0;

        for (MergeHistoryRecord record : history) {
            if (cntThreshold < thresholds.length && record.getDist() > thresholds[cntThreshold]) {

                ClusterSet newSet = clusterSet.copy();
                newSet.setThreshold(thresholds[cntThreshold]);
                snapshots.add(newSet);
                cntThreshold++;
            }

            Cluster cluster1 = clusterSet.getCluster(record.getC1());
            Cluster cluster2 = clusterSet.getCluster(record.getC2());
            try {
                Cluster merged = Cluster.merge(nextId, cluster1, cluster2);
                nextId++;
                clusterSet.removeCluster(record.getC1());
                clusterSet.removeCluster(record.getC2());
                clusterSet.addCluster(merged);
            } catch (Exception e){
                System.err.println(e.toString());
                System.exit(123);
            }

        }

        return snapshots;
    }
}
