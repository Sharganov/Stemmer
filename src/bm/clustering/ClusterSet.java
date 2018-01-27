package bm.clustering;

import java.util.*;

/**
 * A class representing a cluster of clusters obtained by stopping the clustering algorithm at a certain threshold
 * */
public class ClusterSet {

    private Map<Integer, Cluster> dict;
    private float threshold;

    /**
     * Creates the cluster set, specifying the stop threshold of the clustering algorithm.
     * */
    ClusterSet(float threshold) {
        this.dict = new HashMap<>();
        this.threshold = threshold;
    }

    public float getThreshold() {
        return threshold;
    }

    void setThreshold(float threshold) {
        this.threshold = threshold;
    }

    void addCluster(Cluster cluster){
        dict.put(cluster.getId(), cluster);
    }

    void removeCluster(int clusterId) {
        if (dict.keySet().contains(clusterId)) {
            dict.remove(clusterId);
        }
    }

    public Cluster getCluster(int clusterId) {
        return dict.get(clusterId);
    }

    public Set<Integer> getClustersId() {
        return dict.keySet();
    }


    ClusterSet copy() {
        ClusterSet newCopy = new ClusterSet(threshold);

        for (Integer key : dict.keySet()) {
            Cluster newCluster = new Cluster( dict.get(key).getId(), new ArrayList<>(dict.get(key).getWords()));
            newCopy.addCluster(newCluster);
        }

        return newCopy;
    }
}
