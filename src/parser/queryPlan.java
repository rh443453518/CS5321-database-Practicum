package parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import Database_Catalog.Catalog;
import Tuple.Tuple;
import net.sf.jsqlparser.statement.select.PlainSelect;
import physicalOperator.DuplicateEliminationOperators;
import physicalOperator.JoinOperator;
import physicalOperator.Operator;
import physicalOperator.ProjectOperator;
import physicalOperator.ScanOperator;
import physicalOperator.SelectOperator;
import physicalOperator.SortOperator;
import visitor.joinVisitor;
import net.sf.jsqlparser.expression.Expression;

/** Parser to construct the query plan tree from PlainSelect */
public class queryPlan {
	// parse the SQL into a tree structured query plan
	private Operator root;
	
	
	/** queryPlan constructor
	 * @param PlainSelect selectBody: the result parsed from Jsql parser */
	public queryPlan(PlainSelect selectBody) throws IOException {
		
		ArrayList<String> sortedTable = new ArrayList<String>(); // List of table names sorted by sequence appear in Join condtion
		HashMap<String, ArrayList<Expression>> selectConditionMap = new HashMap<String, ArrayList<Expression>>(); 
		// Key: table name, Value: select conditions(>=1) related to table
		HashMap<ArrayList<String>, Expression> joinConditionMap = new HashMap<ArrayList<String>, Expression>();
		// Key: pair of table name, Value: join condition related to tables in the key
		ArrayList<ArrayList<String>> joinPair = new ArrayList<ArrayList<String>>(); // pair of table name, sorted by appear sequence in join
		ArrayList<Operator> nodeBeforeJoin = new ArrayList<Operator>(); // to store operator after scan and select
		HashMap<String, String> pairAlias = new HashMap<String, String>();
		
		
		// TODO use joinVisitor to get the categorized expression we need above
		Expression e = selectBody.getWhere();
		
		
		
		if (selectBody.getWhere() != null){ // if there is WHERE
			joinVisitor jVisitor = new joinVisitor(selectBody);
			e.accept(jVisitor);
			
			pairAlias = jVisitor.getPairAlias();
			
			Catalog data = Catalog.getInstance();
			data.setPairAlias(pairAlias);
			
			
			sortedTable = jVisitor.getJoinTableList(); // original name
			selectConditionMap = jVisitor.getSelectConditionMap(); // key:original value:might be Alias name
			joinConditionMap = jVisitor.getJoinConditionMap(); // key:original value:might be Alias name
			joinPair = jVisitor.getJoinPair(); // original name
			
//			System.out.println(jVisitor.getJoinPair().toString());
//			System.out.println(jVisitor.getJoinConditionMap().toString());
//			System.out.println(jVisitor.getJoinTableList().toString());
//			
			
			// If there is no join condition, add the only table name into sortedTable
			if (sortedTable.isEmpty()){ // only select of one table
				String onlyTable = selectBody.getFromItem().toString();
				
				if (selectBody.getFromItem().getAlias()!=null) {
					String onlyAlias = selectBody.getFromItem().getAlias().toString();
					int index = onlyTable.indexOf(" AS ");
					onlyTable = onlyTable.substring(0, index);
					pairAlias.put(onlyAlias, onlyTable);
				}
				
				sortedTable.add(onlyTable);
				pairAlias.put(onlyTable, onlyTable);
				
			}	
		} else {
			String onlyTable = selectBody.getFromItem().toString();
			
			if (selectBody.getFromItem().getAlias()!=null) {
				String onlyAlias = selectBody.getFromItem().getAlias().toString();
				int index = onlyTable.indexOf(" AS ");
				onlyTable = onlyTable.substring(0, index);
				pairAlias.put(onlyAlias, onlyTable);
			}
			
			sortedTable.add(onlyTable);
			pairAlias.put(onlyTable, onlyTable);
			
			Catalog data = Catalog.getInstance();
			data.setPairAlias(pairAlias);
		}
		

		int tableNum = sortedTable.size(); // number of table involved in this query
		
		
		// add Scan and Select node, processed subtree stored in nodeBeforeJoin
		for(int i = 0; i < tableNum; i++) {
			String tableName = sortedTable.get(i); //original name
			ScanOperator scan =  new ScanOperator(tableName);
			root = scan; //First process scan all the time
			
			//If there are related select expressions of a table, process each of them 
			//to grow the subtree
			if(selectConditionMap.containsKey(tableName)){
				ArrayList<Expression> selectConditionList = selectConditionMap.get(tableName);
				for(Expression eachExpression: selectConditionList){
					SelectOperator select = new SelectOperator(eachExpression, root);
					root = select;
				}
			}
			nodeBeforeJoin.add(root); //finish subtree of this table after scan and select
		}
		
		
		ArrayList<Operator> parentsOfTable = (ArrayList<Operator>) nodeBeforeJoin.clone(); // parents nodes of each table
		
		
		// Join all the scanned and selected table together in sequence, set the root node
		if (tableNum >= 2){ // if there are more than 2 table, which means there are joins
			int n = 1; 
			ArrayList<String> pairKey = joinPair.get(n-1); // key
			Expression joinCondition = null;
			JoinOperator Join = null;
			
			while (n < tableNum){ // when there is still table left to join
				pairKey = joinPair.get(n-1); // get next pair of join table as key
				joinCondition = joinConditionMap.get(pairKey);
				int leftIndex = sortedTable.indexOf(pairKey.get(0));
				int rightIndex = sortedTable.indexOf(pairKey.get(1));
				
				
				Operator leftOriginalParent= parentsOfTable.get(leftIndex);
				Operator rightOriginalParent= parentsOfTable.get(rightIndex);
				
				if (!rightOriginalParent.equals(nodeBeforeJoin.get(rightIndex))) {
					Join = new JoinOperator(rightOriginalParent, leftOriginalParent, joinCondition);
				} else {
					Join = new JoinOperator(leftOriginalParent, rightOriginalParent, joinCondition);
				}
				
				// substitute parents of all the node belong to left child to current highest join
				for (int i = 0; i < parentsOfTable.size(); i++) {
			        if(leftOriginalParent.equals(parentsOfTable.get(i))){
			        	parentsOfTable.set(i, Join);
			        }
				}
				// substitute parents of all the node belong to right child to current highest join
				for (int h = 0; h < parentsOfTable.size(); h++) {
			        if(rightOriginalParent.equals(parentsOfTable.get(h))){
			        	parentsOfTable.set(h, Join);
			        }
				}
				
				n++;
			}
		}
		
		root = parentsOfTable.get(0); // after join (or no join), set root to the highest parents
		
		
		// add projection node to current tree if there is project condition
		// TODO ****NEED MODIFY 
		if (selectBody.getSelectItems().get(0)!="*") {
			ProjectOperator project = new ProjectOperator(selectBody, root);
			root = project;
		}
		
		if (selectBody.getOrderByElements()!=null) {
			SortOperator Sort = new SortOperator(root, selectBody);
			root = Sort;
		}
		
		if (selectBody.getDistinct()!=null) {
			if (selectBody.getOrderByElements()==null){
				List orderList = selectBody.getSelectItems();
				SortOperator Sort = new SortOperator(root,selectBody);
				//System.out.println("hh");
				root = Sort;
			}
			DuplicateEliminationOperators distinct = new DuplicateEliminationOperators(root);
			root = distinct;
		}
		
		
	}
	
	/** @return the root of the entire query plan */
	public Operator getRoot() {
		return root;
	}
}
