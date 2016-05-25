package yueyueGo;

import java.util.ArrayList;
import java.util.Vector;

import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.evaluation.ThresholdCurve;
import weka.classifiers.functions.MultilayerPerceptron;
import weka.classifiers.functions.VotedPerceptron;
import weka.classifiers.trees.J48;
import weka.classifiers.trees.LMT;
import weka.classifiers.trees.REPTree;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;

public class NominalClassifier extends MyClassifier{
	protected double DEFAULT_THRESHOLD=0.7; // 找不出threshold时缺省值。


	public NominalClassifier() {
		super();
		//"voted";//"j48";//"rep";//"lmt";//"mlp";
		WORK_PATH = "C:\\Users\\robert\\Desktop\\提升均线策略\\01-二分类器\\";
		WORK_FILE_PREFIX = WORK_PATH + "交易分析2005-2016 by month-new";		
	}

	@Override
	public  Classifier trainData(Instances train) throws Exception {
		int minNumObj=train.numInstances()/300;
		if (minNumObj<1000){
			minNumObj=1000; //防止树过大
		}
		String batchSize="1000";

		Classifier model=null;
		if("j48".equals(this.classifierName)){
			J48 j48=new J48();
			j48.setBatchSize(batchSize);
			j48.setMinNumObj(minNumObj);
			model=j48;
		}else if ("mlp".equals(this.classifierName)){
			MultilayerPerceptron mlp=new MultilayerPerceptron();
			mlp.setBatchSize(batchSize);
			mlp.setNumDecimalPlaces(6);
			mlp.setHiddenLayers("a");
			model=mlp;
		}else if ("lmt".equals(this.classifierName)){
			LMT lmt=new LMT();
			lmt.setBatchSize(batchSize);
			lmt.setNumDecimalPlaces(6);
			lmt.setMinNumInstances(minNumObj);
			model=lmt;
		}else if ("rep".equals(this.classifierName)){
			REPTree rep=new REPTree();
			rep.setBatchSize(batchSize);
			rep.setNumDecimalPlaces(6);
			rep.setMinNum(minNumObj);
			model=rep;
		}else if ("voted".equals(this.classifierName)){
			VotedPerceptron voted=new VotedPerceptron();
			voted.setBatchSize(batchSize);
			voted.setNumDecimalPlaces(6);
			model=voted;
		}
		model.buildClassifier(train);


		// save model + header
		Vector<Object> v = new Vector<Object>();
		v.add(model);
		v.add(new Instances(train, 0));
		
		saveModelToFiles(model, v);
		System.out.println("Training finished!");
		return model;
	}

	//对模型进行评估
	@Override
	public Vector<Double> evaluateModel(Instances train, Classifier model,
			double sample_limit, double sample_upper, double tp_fp_ratio)
			throws Exception {
		
		System.out.println(" -----------evaluating for FULL Market....");
		Vector<Double> v = doModelEvaluation(train, model, sample_limit,sample_upper, tp_fp_ratio);
		System.out.println(" *********** end of evaluating for FULL Market....");		
		// add HS300
		if (m_sepeperate_eval_HS300==true){
			System.out.println(" -----------evaluating for HS300 INDEX....");
			Instances hs300=FilterData.filterDataForIndex(train, ArffFormat.IS_HS300);
			Vector<Double> v_hs300 = doModelEvaluation(hs300, model, sample_limit,sample_upper, tp_fp_ratio*0.9); //对沪深300的TPFP降低要求
			v.addAll(v_hs300);
			System.out.println(" *********** end of evaluating for HS300 INDEX....");		
		}
		saveEvaluationToFile(v);
		return v;
		
	}

	public Vector<Double> doModelEvaluation(Instances train,Classifier model,double sample_limit, double sample_upper,double tp_fp_ratio)
			throws Exception {
		//评估模型
		Evaluation eval = getEvaluation(train, model,1-EVAL_RECENT_PORTION);
		
		System.out.println("finish evaluating model, try to get best threshold for model...");

		
		// generate curve
		ThresholdCurve tc = new ThresholdCurve();
		int classIndex = 1;
		Instances result = tc.getCurve(eval.predictions(), classIndex);

		double thresholdBottom = 0;
		int round=1;
		while (thresholdBottom == 0 && tp_fp_ratio > TP_FP_BOTTOM_LINE){
			System.out.println("try number: "+round);
			Vector<Double> v_threshold = computeThresholds(sample_limit, sample_upper,tp_fp_ratio, result);
			thresholdBottom=v_threshold.get(0).doubleValue();
			if (thresholdBottom>0)
				break;
			else {
				tp_fp_ratio=tp_fp_ratio*0.95;
				round++;
			}
		}
		if (thresholdBottom==0)  //如果无法找到合适的阀值
			thresholdBottom=DEFAULT_THRESHOLD; //设置下限
		else if (thresholdBottom >0.99) { //计算出阀值过于乐观时
			thresholdBottom =thresholdBottom*0.95;//设置上限
		}
		
		Vector<Double> v = new Vector<Double>();
		v.add(new Double(thresholdBottom));
		//TODO 先将模型阀值上限设为1，以后找到合适的算法再计算。
		double thresholdTop=1; 
		v.add(new Double(thresholdTop));
		return v;
	}


	//具体的模型阀值计算方法
	protected Vector<Double> computeThresholds(double sample_limit, double sample_upper,
			double tp_fp_ratio, Instances result) {
		double thresholdBottom = 0.0;
		double lift_max = 0.0;
		double finalSampleSize = 0.0;
		double sampleSize = 0.0;
		double tp = 0.0;
		double fp = 0.0;
//		double tpr=0;
//		double fpr=0;
		double final_tp=0.0;
		double final_fp=0.0;
		double final_deviation=-999999999.9;
		Attribute att_tp = result.attribute("True Positives");
		Attribute att_fp = result.attribute("False Positives");
//		Attribute att_tpr = result.attribute("True Positive Rate"); 
//		Attribute att_fpr= result.attribute("False Positive Rate");
		Attribute att_lift = result.attribute("Lift");
		Attribute att_threshold = result.attribute("Threshold");
		Attribute att_samplesize = result.attribute("Sample Size");

		for (int i = 0; i < result.numInstances(); i++) {
			Instance curr = result.instance(i);
			sampleSize = curr.value(att_samplesize); // to get sample range
			if (sampleSize >= sample_limit && sampleSize <=sample_upper) {
				tp = curr.value(att_tp);
				fp = curr.value(att_fp);
//				tpr = curr.value(att_tpr);
//				fpr = curr.value(att_fpr);
				if (tp>fp*tp_fp_ratio){
					//TODO 试了求TPR-FPR的最大值(tpr-fpr)，效果差不多，先恢复原始的
					if (tp-fp > final_deviation ) {
						thresholdBottom = curr.value(att_threshold);
						finalSampleSize = sampleSize;
						lift_max=curr.value(att_lift);
						final_tp=tp;
						final_fp=fp;
						final_deviation=tp-fp;
					}
				}
			}
		}
		System.out.print("thresholdBottom is : " + FormatUtility.formatDouble(thresholdBottom));
		System.out.print("/samplesize is : " + FormatUtility.formatPercent(finalSampleSize) );
		System.out.print("/True Positives is : " + final_tp);
		System.out.print("/False Positives is : " + final_fp);
		System.out.println("/lift max is : " + FormatUtility.formatDouble(lift_max));
		

		Vector<Double> v = new Vector<Double>();
		v.add(new Double(thresholdBottom));
		return v;
	}

	//对于二分类变量，返回分类1的预测可能性
	@Override
	protected  double classify(Classifier model,Instance curr) throws Exception {
		double[] problity =  model.distributionForInstance(curr);
		return problity[1];
	}
	
	//将原始数据变换为nominal Classifier需要的形式（更换class 变量等等）
	public Instances processDataForNominalClassifier(Instances inData) throws Exception{
		//Attribute oldClassAtt=inData.attribute(ArffFormat.SHOUYILV);
		ArrayList<String> values=new ArrayList<String>();
		values.add("0");
		values.add("1");
		Attribute newClassAtt=new Attribute(ArffFormat.IS_POSITIVE,values);
		//在classValue之前插入positve,然后记录下它的新位置index
		inData.insertAttributeAt(newClassAtt, inData.numAttributes()-1);
		int newClassIndex=inData.numAttributes()-2;
		double shouyilv=0;
		for (int i=0;i<inData.numInstances();i++){
			shouyilv=inData.instance(i).classValue();
			if (shouyilv>0){
				inData.instance(i).setValue(newClassIndex, "1");
			}else {
				inData.instance(i).setValue(newClassIndex, "0");
			}
		}
		//删除shouyilv
		inData=FilterData.removeAttribs(inData, ""+inData.numAttributes());
		//设置新属性的位置
		inData.setClassIndex(inData.numAttributes()-1);
		
		
		System.out.println("class value replaced for nominal classifier");
		return inData;
	}
	

}