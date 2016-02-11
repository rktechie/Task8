package controller;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import model.FundDAO;
import model.FundPriceHistoryDAO;
import model.Model;
import model.PositionDAO;
import model.TransactionDAO;

import org.genericdao.MatchArg;
import org.genericdao.RollbackException;
import org.genericdao.Transaction;
import org.mybeans.form.FormBeanException;
import org.mybeans.form.FormBeanFactory;

import com.google.gson.Gson;

import databean.CustomerBean;
import databean.FundBean;
import databean.PositionBean;
import databean.PositionInfo;
import databean.TransactionBean;
import formbean.SellFundForm;

public class SellFundAction extends Action {
	private FormBeanFactory<SellFundForm> formBeanFactory = FormBeanFactory.getInstance(SellFundForm.class);

	private TransactionDAO transactionDAO;
	private FundDAO fundDAO;
	private PositionDAO positionDAO;
	private FundPriceHistoryDAO historyDAO;

	public SellFundAction(Model model) {
		transactionDAO = model.getTransactionDAO();
		fundDAO = model.getFundDAO();
		positionDAO = model.getPositionDAO();
		historyDAO = model.getFundPriceHistoryDAO();
	}

	public String getName() {
		return "sellFund";
	}

	public String perform(HttpServletRequest request) {
		List<String> errors = new ArrayList<String>();
		request.setAttribute("errors", errors);
		HttpSession session = request.getSession();
		Gson gson = new Gson();
		ReturnJson returnJson = new ReturnJson();

		try {
			SellFundForm sellFundForm = formBeanFactory.create(request);
			request.setAttribute("form", sellFundForm);

			if (session.getAttribute("user") == null) {
				returnJson.message = "You must log in prior to making this request";
				return gson.toJson(returnJson.message);
			}
			
			if (!sellFundForm.isPresent()) {
				returnJson.message = "Input Parameters could not be read.";
				return gson.toJson(returnJson);
			}
			
			DecimalFormat df3 = new DecimalFormat("#,##0.000");
			DecimalFormat df2 = new DecimalFormat(	"###,##0.00");
			CustomerBean customerBean = (CustomerBean) session.getAttribute("user");

			PositionBean[] positionList = positionDAO.match(MatchArg.equals("customerId",customerBean.getCustomerId()));
			
			if(positionList != null) {
				List<PositionInfo> positionInfoList = new ArrayList<PositionInfo>();
				for(PositionBean a: positionList) {
					double shares = ((double)(a.getShares())/1000.0);

					double price = ((double)(historyDAO.getLatestFundPrice(a.getFundId()).getPrice() / 100.0));
					double value = shares * price;
					String name=fundDAO.read(a.getFundId()).getName();

					String sharesString = df3.format(shares);
					String priceString = df2.format(price);
					String valueString = df2.format(value);

					PositionInfo aInfo = new PositionInfo(name,sharesString,priceString,"$" + valueString);
					positionInfoList.add(aInfo);
				}
				session.setAttribute("positionInfoList",positionInfoList);
			}

			errors.addAll(sellFundForm.getValidationErrors());
			if (errors.size() != 0) {
				returnJson.message = errors.get(0);
				return gson.toJson(returnJson.message);
			}
			String userName = customerBean.getUserName();
			int customerId = customerBean.getCustomerId();

			// Get the fund ID of the fund name in form
			FundBean fundBean = fundDAO.read(sellFundForm.getName());
			if (fundBean == null) {
//				errors.add("Fund does not exist");
				returnJson.message = "Fund does not exist";
				return gson.toJson(returnJson.message);
			}
			int fundId = fundBean.getFundId();
			// How to determine whether this customer own this fund or not
			PositionBean position = positionDAO.read(customerId, fundId);
			if (position == null) {
//				errors.add("You do not own this fund!");
				returnJson.message = "You do not own this fund";
				return gson.toJson(returnJson.message);
			}
			double curShares = (double)position.getShares() / 1000;
			double shares =  Double.parseDouble(sellFundForm.getShares());
			if (shares == 0) {
//				errors.add("You can not sell zero shares");
				returnJson.message = "You can not sell zero shares";
				return gson.toJson(returnJson.message);
			}
			if ((shares * 1000.0 - (long) (shares * 1000.0)) > 0) {
//				errors.add("We only allow at most three decimal for shares");
				returnJson.message = "We only allow at most three decimal for shares";
				return gson.toJson(returnJson.message);
			}
			
			//Check valid shares
			if (curShares < shares) {
//				errors.add("You do not have enough shares!");
				returnJson.message = "You do not have enough shares";
				return gson.toJson(returnJson.message);
			}
			double validShares = transactionDAO.getValidShares(customerId, fundId, curShares);
			if (shares > validShares) {
//				errors.add("You do not have enough shares! (including pending transaction)");
				returnJson.message = "You do not have enough shares! (including pending transaction)";
				return gson.toJson(returnJson.message);
			}

			// Create a transaction bean
			Transaction.begin();
			TransactionBean transactionBean = new TransactionBean();
			transactionBean.setCustomerId(customerId);
			transactionBean.setFundId(fundId);
			transactionBean.setUserName(userName);
			transactionBean.setShares((long) (shares * 1000l));
			transactionBean.setTransactionType("4");
			transactionDAO.create(transactionBean);
			Transaction.commit();
			request.removeAttribute("form");
			returnJson.message = "The account has been successfully updated";
			return gson.toJson(returnJson.message);
			
		} catch (NumberFormatException e) {
			errors.add(e.getMessage());
			returnJson.message = "I’m sorry, there was a problem selling funds";
			return gson.toJson(returnJson.message); 
		} catch (RollbackException e) {
			errors.add(e.getMessage());
			returnJson.message = "I’m sorry, there was a problem selling funds";
			return gson.toJson(returnJson.message); 
		} catch (FormBeanException e) {
			errors.add(e.getMessage());
			returnJson.message = "I’m sorry, there was a problem selling funds";
			return gson.toJson(returnJson.message); 
		}
	}

	private class ReturnJson {
		String message;
	}
}
