package edu.macalester.wpsemsim.sim;

import edu.macalester.wpsemsim.matrix.TSVMatrixWriter;
import edu.macalester.wpsemsim.sim.utils.EnvConfigurator;
import edu.macalester.wpsemsim.utils.ConfigurationFile;
import edu.macalester.wpsemsim.utils.KnownSim;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * Created with IntelliJ IDEA.
 * User: aaron
 * Date: 1/18/13
 * Time: 3:42 PM
 * To change this template use File | Settings | File Templates.
 */
public class Interpolator{

    private int Degree = 1;
    private SimilarityMetric similarityMetric;
    public static Logger LOG = Logger.getLogger(TSVMatrixWriter.class.getName());
    private List<KnownSim> gold;

    // for c_0 +c_1 x + c_2 x^2 +c_3 x^3 ...
    // model of form {upper bound, c_0, c_1, c_2, c_3, ...}
    private double[][] model;

    public Interpolator(String goldStandard, String config, String metric) throws IOException, ConfigurationFile.ConfigurationException {
        EnvConfigurator conf = new EnvConfigurator(
                new ConfigurationFile(new File(config)));
        this.gold = KnownSim.read(new File(goldStandard));
        this.similarityMetric=conf.loadMetric(metric);
    }
    public Interpolator(String goldStandard, SimilarityMetric metric) throws IOException{
        this.gold= KnownSim.read(new File(goldStandard));
        this.similarityMetric=metric;
    }
    public Interpolator(List<KnownSim> goldStandard, SimilarityMetric metric){
        this.gold=goldStandard;
        this.similarityMetric=metric;
    }


    public double interpolate(double raw){
        for (double[] i:model){
            if(raw<i[0]){
                Double fit =0.0;
                for (int j=1; j<i.length;j++){
                    fit += i[j]*Math.pow(raw,(j-1));
                }
                return fit;
            }
        }
        return Double.NaN;
    }

    public void mkModel(){
        List<double[]> allPoints=getAllPoints();
        int nb = modeCount(allPoints);
        nb = Math.max((int) Math.ceil(Math.min(allPoints.size() / 50, allPoints.size() / ((nb + 1) / 2))), 3);
        double[][] avgPoints=new double[nb-1][2];
        for (int i=0; i<avgPoints.length;i++){
            avgPoints[i]=avgPoint(allPoints.subList((int) Math.floor(i*allPoints.size()/nb),(int) Math.max(Math.ceil(1.0*allPoints.size()/nb*(i+1)),allPoints.size()-1)));
        }
        if (Degree==1){
            double[][] mod = new double[avgPoints.length-1][3];
            for (int i=0; i<avgPoints.length-1;i++){
                mod[i][0]=avgPoints[i+1][0];
                mod[i][2]=(avgPoints[i+1][1]-avgPoints[i][1])/(avgPoints[i+1][0]-avgPoints[i][0]);
                mod[i][1]=avgPoints[i+1][1]-(mod[i][2]*avgPoints[i+1][0]);
            }
            mod[mod.length-1][0]=Double.POSITIVE_INFINITY;
            model=mod;
        }
    }

    /* write/load format:
     * degree
     * #points
     * x_0\tc_0,0\tc_0,1\tc_0,2...c_0,degree
     * x_1\tc_1,0\tc_1,1\tc_1,2...c_1,degree
     * ...
     * x_#points\t...c_#points,degree
     */
    public void readModel(String path) throws FileNotFoundException{
        File f = new File(path);
        Scanner fileScan=new Scanner(new FileReader(f));
        Scanner lineScan;
        int degree=Integer.parseInt(fileScan.nextLine().trim());
        int numX = Integer.parseInt(fileScan.nextLine().trim());
        double[][] mod = new double[numX][degree+2];
        int i=0;
        while (fileScan.hasNext()){
            lineScan=new Scanner(fileScan.nextLine());
            lineScan.useDelimiter("\t");
            for (int j=0; j<degree+2;j++){
                mod[i][j]=lineScan.nextDouble();
            }
            i++;
            if (i==mod.length) break;
        }
        fileScan.close();
        model=mod;
        Degree=degree;
    }

    public void writeModel(String path) throws IOException{
        BufferedWriter writer = new BufferedWriter(new FileWriter(path));
        Degree=model[0].length-2;
        writer.write(""+Degree+"\n");
        writer.write(""+model.length+"\n");
        for (int i=0;i<model.length;i++){
            String line ="";
            line=line+model[i][0];
            for (int j=1;j<model[i].length;j++) {
                line=line+"\t"+model[i][j];
            }
            line =line+"\n";
            writer.write(line);
        }
        writer.close();
    }

    public int modeCount(List<double[]> points){
        int maxCount=0;
        for (int i=0; i< points.size();i++){
            int count=0;
            for (int j=0; j<points.size();j++){
                if (points.get(i)[0]==points.get(j)[0]) count++;
            }
            if (count>maxCount){
                maxCount=count;
            }
        }
        return maxCount;
    }

    public double[] avgPoint(List<double[]> points){
        double[] x = new double[points.size()];
        double[] y = new double[points.size()];
        for (int i = 0; i < points.size(); i++){
            x[i] = points.get(i)[0];
            y[i] = points.get(i)[1];
        }
        return new double[]{mean(x), mean(y)};
    }

    public double mean(double[] nums){
        double sum=0.0;
        for (double i : nums){
            sum+=i;
        }
        return sum/(nums.length);
    }

    public List<double[]> getAllPoints(){
        List<double[]> points=new ArrayList<double[]>();
        for (int i = 0; i<gold.size();i++){
            final KnownSim ks = gold.get(i);
            try{
                double sim =similarityMetric.similarity(ks.phrase1, ks.phrase2);
                if (!Double.isInfinite(sim) && !Double.isNaN(sim)) {
                    points.add(new double[]{sim, ks.similarity});
                }
            } catch (Exception e){}
        }
        Collections.sort(points,new Comparator<double[]>() {
            @Override
            public int compare(double[] doubles, double[] doubles2) {
                if(doubles[0]>doubles2[0]){
                    return 1;
                }
                return 0;  //To change body of implemented methods use File | Settings | File Templates.
            }
        });
        return points;
    }
}
