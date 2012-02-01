package applab.metricCalculator;

import java.rmi.RemoteException;
import java.util.ArrayList;

import javax.xml.rpc.ServiceException;

import com.sforce.soap.enterprise.LoginResult;
import com.sforce.soap.enterprise.QueryResult;
import com.sforce.soap.enterprise.SessionHeader;
import com.sforce.soap.enterprise.SforceServiceLocator;
import com.sforce.soap.enterprise.SoapBindingStub;
import com.sforce.soap.enterprise.UpsertResult;
import com.sforce.soap.enterprise.fault.InvalidIdFault;
import com.sforce.soap.enterprise.fault.LoginFault;
import com.sforce.soap.enterprise.fault.UnexpectedErrorFault;
import com.sforce.soap.enterprise.sobject.M_E_Metric_Data__c;

/**
 * Helper class to deal with SF interactions
 *
 * Copyright (C) 2012 Grameen Foundation
 */

public class SalesforceProxy {

    private static SoapBindingStub binding;

    public static void initBinding() throws InvalidIdFault, UnexpectedErrorFault, LoginFault, RemoteException, ServiceException {

        SforceServiceLocator serviceLocator = new SforceServiceLocator();
        serviceLocator.setSoapEndpointAddress(Configuration.getConfiguration("salesforceAddress", ""));
        binding = (SoapBindingStub)serviceLocator.getSoap();
        LoginResult loginResult = binding.login(
                Configuration.getConfiguration("salesforceUsername", ""),
                Configuration.getConfiguration("salesforcePassword", "")
                        + Configuration.getConfiguration("salesforceToken", ""));

        binding._setProperty(SoapBindingStub.ENDPOINT_ADDRESS_PROPERTY,
                loginResult.getServerUrl());

        SessionHeader sessionHeader = new SessionHeader(
                loginResult.getSessionId());
        binding.setHeader(serviceLocator.getServiceName().getNamespaceURI(),
                "SessionHeader", sessionHeader);
    }

    public static void getBinding() throws InvalidIdFault, UnexpectedErrorFault, LoginFault, RemoteException, ServiceException {

        if (binding == null) {
            initBinding();
        }
    }

    public static QueryResult getSalesforceObjects(String query) throws RemoteException, ServiceException {

        getBinding();
        return binding.query(query);
   }

    public static QueryResult getSalesforceObjectsMore(String queryLocator) throws RemoteException, ServiceException {

        getBinding();
        return binding.queryMore(queryLocator);
   }

    public static ArrayList<M_E_Metric_Data__c> saveDatasToSalesforce(ArrayList<M_E_Metric_Data__c> datas) throws RemoteException {

        ArrayList<M_E_Metric_Data__c> failedDatas = new ArrayList<M_E_Metric_Data__c>();
        UpsertResult[] dataSaveResult = binding.upsert("ID", datas.toArray(new M_E_Metric_Data__c[0]));
        for (int i = 0; i < dataSaveResult.length; i ++) {
            if (!dataSaveResult[i].isSuccess()) {
                System.out.println(dataSaveResult[i].getErrors()[0].getMessage());
                failedDatas.add(datas.get(i));
            }
        }
        return failedDatas;
    }

    public static void deleteRecords(ArrayList<String> ids) throws UnexpectedErrorFault, RemoteException, ServiceException {

        getBinding();
        binding.delete(ids.toArray(new String[0]));
    }
}
