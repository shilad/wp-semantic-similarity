package edu.macalester.wpsemsim.normalize;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: aaron
 * Date: 1/23/13
 * Time: 1:29 PM
 * To change this template use File | Settings | File Templates.
 */
public class PolinomialInterpolatorNormalizer extends BaseNormalizer {
    private List<double[]> allPoints=new ArrayList<double[]>();

    private int Degree=1;
    private boolean finalized=false;

    private boolean Supervised=true;

    // for c_0 +c_1 x + c_2 x^2 +c_3 x^3 ...
    // model of form {upper bound, c_0, c_1, c_2, c_3, ...}
    private double[][] model;

    private double[][] nodes;

    public int getDegree(){
        return Degree;
    }

    public void setDegree(int x){
        if (x != Degree){
            Degree=x;
            if (finalized){
                mkModel();
            }
        }
    }

    public double[][] getNodes(){
        return nodes;
    }

    public List<double[]> getAllPoints(){
        return allPoints;
    }

    public double[][] getModel(){
        return model;
    }

    @Override
    public void observe(double x, double y){
        double[] point=new double[]{x,y};
        allPoints.add(point);
    }

    @Override
    public void observationsFinished(){
        Collections.sort(allPoints, new Comparator<double[]>() {
            @Override
            public int compare(double[] doubles, double[] doubles2) {
                if (doubles[0]<doubles2[0]){
                    return -1;
                } else if (doubles[0]>doubles2[0]){
                    return 1;
                }
                return 0;
            }
        });
        finalized=true;
        mkModel();
    }

    @Override
    public double normalize(double x) {
        return Math.max(Math.min(interpolate(x), 1.0), 0);  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public double unnormalize(double x) {
        return normalize(x);  //To change body of implemented methods use File | Settings | File Templates.
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
        int nb = modeCount(allPoints);
        nb = Math.max((int) Math.ceil(Math.min(allPoints.size() / 50, allPoints.size() / ((nb + 1) / 2))), 4);
        double[][] avgPoints=new double[nb][2];
        int diff=(int) Math.ceil(((double)allPoints.size())/((double) nb));
        for (int i=0; i<avgPoints.length;i++){
            avgPoints[i]=avgPoint(allPoints.subList(i*diff,(i+1)*diff));
        }
        nodes=avgPoints;
        if (Degree==1){
            double[][] mod = new double[avgPoints.length-1][3];
            for (int i=0; i<avgPoints.length-1;i++){
                mod[i][0]=avgPoints[i+1][0];
                mod[i][2]=(avgPoints[i+1][1]-avgPoints[i][1])/(avgPoints[i+1][0]-avgPoints[i][0]);
                mod[i][1]=avgPoints[i+1][1]-(mod[i][2]*avgPoints[i+1][0]);
            }
            mod[mod.length-1][0]=Double.POSITIVE_INFINITY;
            model=mod;
        } else{
            model= new double[0][0];
        }
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
        return sum/((double) nums.length);
    }
}
