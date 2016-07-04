package yueyueGo;

import weka.core.Instances;
import weka.experiment.InstanceQuery;

public class DBAccess  {
	public final static String URL = "jdbc:mysql://uts.simu800.com/develop?characterEncoding=utf8&autoReconnect=true";
	public final static String USER = "root";
	public final static String PASSWORD = "data@2014";
	//protected final static String QUERY_DATA = "SELECT  `id`, `selected_avgline`, `bias5`,  `bias10`,  `bias20`,  `bias30`,  `bias60`, `bias5_preday_dif`,  `bias10_preday_dif`,  `bias20_preday_dif`,  `bias30_preday_dif`,  `bias60_preday_dif`,  `bias5_pre2day_dif`,  `bias10_pre2day_dif`,  `bias20_pre2day_dif`,  `bias30_pre2day_dif`,  `bias60_pre2day_dif`, `ma5_preday_perc`,  `ma10_preday_perc`, `ma20_preday_perc`,`ma30_preday_perc`,`ma60_preday_perc`,`ma5_pre2day_perc`, `ma10_pre2day_perc`,`ma20_pre2day_perc`,`ma30_pre2day_perc`,`ma60_pre2day_perc`,`ma5_pre3day_perc`, `ma10_pre3day_perc`,`ma20_pre3day_perc`,`ma30_pre3day_perc`,`ma60_pre3day_perc`,`ma5_pre4day_perc`, `ma10_pre4day_perc`,`ma20_pre4day_perc`,`ma30_pre4day_perc`,`ma60_pre4day_perc`,`ma5_pre5day_perc`, `ma10_pre5day_perc`,`ma20_pre5day_perc`,`ma30_pre5day_perc`,`ma60_pre5day_perc`, `zhangdiefu`,  `huanshoulv`, `huanshoulv_preday_perc`,  `huanshoulv_pre2day_perc`,  `huanshoulv_pre3day_perc`, `zhishu_code`,   `sw_zhishu_code`, `issz50`,  `ishs300`, `iszz100`, `iszz500`, `issz100`,  `ishgtb`,   `isrzbd`, `sw_bias5`, `sw_bias10`, `sw_bias20`, `sw_bias30`, `sw_bias60`, `sw_bias5_preday_dif`, `sw_bias10_preday_dif`, `sw_bias20_preday_dif`, `sw_bias30_preday_dif`,  `sw_bias60_preday_dif`, `sw_bias5_pre2day_dif`, `sw_bias10_pre2day_dif`,   `sw_bias20_pre2day_dif`,   `sw_bias30_pre2day_dif`,   `sw_bias60_pre2day_dif`, `zhishu_bias5`, `zhishu_bias10`, `zhishu_bias20`,  `zhishu_bias30`,   `zhishu_bias60`,  `zhishu_bias5_preday_dif`, `zhishu_bias10_preday_dif`,  `zhishu_bias20_preday_dif`, `zhishu_bias30_preday_dif`,  `zhishu_bias60_preday_dif`,  `zhishu_bias5_pre2day_dif`,   `zhishu_bias10_pre2day_dif`,  `zhishu_bias20_pre2day_dif`, `zhishu_bias30_pre2day_dif`, `zhishu_bias60_pre2day_dif`,  `shouyilv` FROM t_stock_avgline_increment_zuixin_v ";
			
	
	
	public DBAccess() {
		// TODO Auto-generated constructor stub
	}
	
	private static String generateQueryData(int format){
		
		String queryData="SELECT ";
		String[] target_columns=null;
		String target_view=null;
		
		switch (format) {
		case ArffFormat.NORMAL_FORMAT:
			target_columns=ArffFormat.DAILY_DATA_TO_PREDICT_FORMAT;
			target_view="t_stock_avgline_increment_zuixin_v";
			break;
		case ArffFormat.EXT_FORMAT:
			target_columns=ArffFormat.EXT_DAILY_DATA_TO_PREDICT_FORMAT;
			target_view="t_stock_avgline_increment_zuixin_group3";
			break;			
		default:
			break;
		}
		for (int i=0;i<target_columns.length;i++){
			queryData+= " `"+target_columns[i]+"`";
			if (i<target_columns.length-1){
				queryData+=", ";
			}
		}
		queryData+=" FROM "+target_view;
		System.out.println(queryData);
		return queryData;
	}
	
	public static Instances LoadDataFromDB(int format) throws Exception{
//		DatabaseLoader loader = new DatabaseLoader();
//		loader.setUrl(URL);
//		loader.setUser(USER);
//		loader.setPassword(PASSWORD);
//		String queryData=generateQueryData();
//		loader.setQuery(queryData); 
//		Instances data=loader.getDataSet();

		
		//load data from database that needs predicting
		InstanceQuery query = new InstanceQuery();
		query.setDatabaseURL(URL);
		query.setUsername(USER);
		query.setPassword(PASSWORD);
		String queryData=generateQueryData(format);
		query.setQuery(queryData); 

		Instances data = query.retrieveInstances();
		//全部读进来之后再转nominal，这里读入的数据可能只是子集，所以nominal的index值会不对，所以后续会用calibrateAttributes处理
		data=FilterData.numToNominal(data, "2,48-56");

		//读入数据后最后一行加上为空的收益率
		data = FilterData.AddAttribute(data, ArffFormat.SHOUYILV,data.numAttributes());
		
		//把读入的数据改名 以适应内部训练的arff格式,读入的数据里多了第一列的ID
		data=ArffFormat.trainingAttribMapper(data,ArffFormat.DAILY_DATA_TO_PREDICT_FORMAT,1);
		data.setClassIndex(data.numAttributes()-1);
		System.out.println("records loaded from database: "+data.numInstances());
		return data;
	}



}
