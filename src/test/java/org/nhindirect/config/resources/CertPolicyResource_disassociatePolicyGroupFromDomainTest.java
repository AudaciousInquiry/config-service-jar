package org.nhindirect.config.resources;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;

import org.junit.Test;
import org.nhindirect.config.BaseTestPlan;
import org.nhindirect.config.SpringBaseTest;
import org.nhindirect.config.model.Address;
import org.nhindirect.config.model.CertPolicy;
import org.nhindirect.config.model.CertPolicyGroup;
import org.nhindirect.config.model.Domain;
import org.nhindirect.config.model.EntityStatus;
import org.nhindirect.config.store.dao.CertPolicyDao;
import org.nhindirect.config.store.dao.DomainDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;

public class CertPolicyResource_disassociatePolicyGroupFromDomainTest extends SpringBaseTest
{
	@Autowired
	protected CertPolicyResource certService;	
	
		abstract class TestPlan extends BaseTestPlan 
		{
			protected Collection<CertPolicyGroup> groups;
			
			protected Collection<CertPolicy> policies;
			
			
			@Override
			protected void tearDownMocks()
			{

			}

			
			protected  Collection<CertPolicyGroup> getGroupsToAdd()
			{
				try
				{
					groups = new ArrayList<CertPolicyGroup>();
					
					CertPolicyGroup group = new CertPolicyGroup();
					group.setPolicyGroupName("Group1");
					groups.add(group);
					
					group = new CertPolicyGroup();
					group.setPolicyGroupName("Group2");
					groups.add(group);
					
					return groups;
				}
				catch (Exception e)
				{
					throw new RuntimeException (e);
				}
			}
			
			
			protected  Domain getDomainToAdd()
			{
				final Address postmasterAddress = new Address();
				postmasterAddress.setEmailAddress("me@test.com");
				
				Domain domain = new Domain();
				
				domain.setDomainName("test.com");
				domain.setStatus(EntityStatus.ENABLED);
				domain.setPostmasterAddress(postmasterAddress);			
				
				return domain;
			}
			
			
			
			protected  String getGroupNameToAssociate()
			{
				return "Group1";
			}
			
			protected  String getDomainNameToAssociate()
			{
				return "test.com";
			}
			
			protected abstract String getGroupNameToDisassociate();
			
			protected abstract String getDomainNameToDisassociate();
			
			@Override
			protected void performInner() throws Exception
			{				
				final Domain addDomain = getDomainToAdd();
				
				if (addDomain != null)
				{
					final HttpEntity<Domain> requestEntity = new HttpEntity<>(addDomain);
					final ResponseEntity<Void> resp = testRestTemplate.exchange("/domain", HttpMethod.PUT, requestEntity, Void.class);
					if (resp.getStatusCodeValue() != 201)
						throw new HttpClientErrorException(resp.getStatusCode());
				}
				
				final Collection<CertPolicyGroup> groupsToAdd = getGroupsToAdd();
				
				if (groupsToAdd != null)
				{
					groupsToAdd.forEach(addGroup->
					{
						final HttpEntity<CertPolicyGroup> requestEntity = new HttpEntity<>(addGroup);
						final ResponseEntity<Void> resp = testRestTemplate.exchange("/certpolicy/groups", HttpMethod.PUT, requestEntity, Void.class);
						if (resp.getStatusCodeValue() != 201)
							throw new HttpClientErrorException(resp.getStatusCode());
					});	
				}
				

				// associate the bundle and domain
				if (groupsToAdd != null && addDomain != null)
				{					
					final ResponseEntity<Void> resp = testRestTemplate.exchange("/certpolicy/groups/domain/{groupName}/{domainName}", HttpMethod.POST, null, Void.class,
							getGroupNameToAssociate(), getDomainNameToAssociate());
					if (resp.getStatusCodeValue() != 204)
						throw new HttpClientErrorException(resp.getStatusCode());
				}
				
				
				// disassociate				
				final ResponseEntity<Void> resp = testRestTemplate.exchange("/certpolicy/groups/domain/{groupName}/{domainName}", HttpMethod.DELETE, null, Void.class,
						getGroupNameToDisassociate(), getDomainNameToDisassociate());
				if (resp.getStatusCodeValue() != 200)
					throw new HttpClientErrorException(resp.getStatusCode());

				doAssertions();
				
			}
				
			protected void doAssertions() throws Exception
			{
				
			}
		}	
		
		@Test
		public void testDisassociatePolicyGroupFromDomain_assertGroupDomainDisassociated()  throws Exception
		{
			new TestPlan()
			{

				@Override
				protected String getGroupNameToDisassociate()
				{
					return "Group1";
				}
				
				@Override
				protected String getDomainNameToDisassociate()
				{
					return "test.com";
				}
				
				@Override
				protected void doAssertions() throws Exception
				{
					final org.nhindirect.config.store.Domain domain = domainDao.getDomainByName(getDomainNameToAssociate());
					
					final Collection<org.nhindirect.config.store.CertPolicyGroupDomainReltn> reltns = policyDao.getPolicyGroupsByDomain(domain.getId());
					
					assertEquals(0, reltns.size());
				}
			}.perform();
		}	
		
		@Test
		public void testDisassociatePolicyGroupFromDomain_unknownGroup_assertNotFound()  throws Exception
		{
			new TestPlan()
			{

				@Override
				protected String getGroupNameToDisassociate()
				{
					return "Group4";
				}
				
				@Override
				protected String getDomainNameToDisassociate()
				{
					return "test.com";
				}
				
				@Override
				protected void assertException(Exception exception) throws Exception 
				{
					assertTrue(exception instanceof HttpClientErrorException);
					HttpClientErrorException ex = (HttpClientErrorException)exception;
					assertEquals(404, ex.getRawStatusCode());
				}
			}.perform();
		}	
		
		@Test
		public void testDisassociatePolicyGroupFromDomain_unknownDomain_assertNotFound()  throws Exception
		{
			new TestPlan()
			{

				@Override
				protected String getGroupNameToDisassociate()
				{
					return "Group1";
				}
				
				@Override
				protected String getDomainNameToDisassociate()
				{
					return "test.com1";
				}
				
				@Override
				protected void assertException(Exception exception) throws Exception 
				{
					assertTrue(exception instanceof HttpClientErrorException);
					HttpClientErrorException ex = (HttpClientErrorException)exception;
					assertEquals(404, ex.getRawStatusCode());
				}
			}.perform();
		}	
		
		@Test
		public void testDisassociatePolicyGroupFromDomain_errorInGroupLookup_assertServiceError()  throws Exception
		{
			new TestPlan()
			{

				@Override
				protected void setupMocks()
				{
					try
					{
						super.setupMocks();
						CertPolicyDao mockDAO = mock(CertPolicyDao.class);
						doThrow(new RuntimeException()).when(mockDAO).getPolicyGroupByName((String)any());
						
						certService.setCertPolicyDao(mockDAO);
					}
					catch (Throwable t)
					{
						throw new RuntimeException(t);
					}
				}
				
				@Override
				protected void tearDownMocks()
				{
					super.tearDownMocks();
					
					certService.setCertPolicyDao(policyDao);
				}
				
				@Override
				protected  Collection<CertPolicyGroup> getGroupsToAdd()
				{
					return null;
				}
				
				@Override
				protected  Domain getDomainToAdd()
				{
					return null;
				}
				
				@Override
				protected String getGroupNameToDisassociate()
				{
					return "Group1";
				}
				
				@Override
				protected String getDomainNameToDisassociate()
				{
					return "test.com1";
				}
				
				@Override
				protected void assertException(Exception exception) throws Exception 
				{
					assertTrue(exception instanceof HttpClientErrorException);
					HttpClientErrorException ex = (HttpClientErrorException)exception;
					assertEquals(500, ex.getRawStatusCode());
				}
			}.perform();
		}
		
		@Test
		public void testDisassociatePolicyGroupFromDomain_errorInDomainLookup_assertServiceError()  throws Exception
		{
			new TestPlan()
			{
				@Override
				protected void setupMocks()
				{
					try
					{
						super.setupMocks();
						CertPolicyDao mockPolicyDAO = mock(CertPolicyDao.class);
						DomainDao mockDomainDAO = mock(DomainDao.class);
						
						when(mockPolicyDAO.getPolicyGroupByName("Group1")).thenReturn(new org.nhindirect.config.store.CertPolicyGroup());
						doThrow(new RuntimeException()).when(mockDomainDAO).getDomainByName((String)any());
						
						certService.setCertPolicyDao(mockPolicyDAO);
						certService.setDomainDao(mockDomainDAO);
					}
					catch (Throwable t)
					{
						throw new RuntimeException(t);
					}
				}
				
				@Override
				protected void tearDownMocks()
				{
					super.tearDownMocks();
					
					certService.setCertPolicyDao(policyDao);
					certService.setDomainDao(domainDao);
				}
				
				@Override
				protected  Collection<CertPolicyGroup> getGroupsToAdd()
				{
					return null;
				}
				
				@Override
				protected  Domain getDomainToAdd()
				{
					return null;
				}
				
				@Override
				protected String getGroupNameToDisassociate()
				{
					return "Group1";
				}
				
				@Override
				protected String getDomainNameToDisassociate()
				{
					return "test.com1";
				}
				
				@Override
				protected void assertException(Exception exception) throws Exception 
				{
					assertTrue(exception instanceof HttpClientErrorException);
					HttpClientErrorException ex = (HttpClientErrorException)exception;
					assertEquals(500, ex.getRawStatusCode());
				}
			}.perform();
		}		
		
		@Test
		public void testDisassociatePolicyGroupFromDomain_errorInDisassociate_assertServiceError()  throws Exception
		{
			new TestPlan()
			{
				@Override
				protected void setupMocks()
				{
					try
					{
						super.setupMocks();

						CertPolicyDao mockPolicyDAO = mock(CertPolicyDao.class);
						DomainDao mockDomainDAO = mock(DomainDao.class);
						
						when(mockPolicyDAO.getPolicyGroupByName("Group1")).thenReturn(new org.nhindirect.config.store.CertPolicyGroup());
						when(mockDomainDAO.getDomainByName("test.com")).thenReturn(new org.nhindirect.config.store.Domain());
						doThrow(new RuntimeException()).when(mockPolicyDAO).disassociatePolicyGroupFromDomain(eq(0L), eq(0L));
						
						certService.setCertPolicyDao(mockPolicyDAO);
						certService.setDomainDao(mockDomainDAO);
					}
					catch (Throwable t)
					{
						throw new RuntimeException(t);
					}
				}
				
				@Override
				protected void tearDownMocks()
				{
					super.tearDownMocks();
					
					certService.setCertPolicyDao(policyDao);
					certService.setDomainDao(domainDao);
				}
				
				@Override
				protected  Collection<CertPolicyGroup> getGroupsToAdd()
				{
					return null;
				}
				
				@Override
				protected  Domain getDomainToAdd()
				{
					return null;
				}
				
				@Override
				protected String getGroupNameToDisassociate()
				{
					return "Group1";
				}
				
				@Override
				protected String getDomainNameToDisassociate()
				{
					return "test.com";
				}
				
				@Override
				protected void assertException(Exception exception) throws Exception 
				{
					assertTrue(exception instanceof HttpClientErrorException);
					HttpClientErrorException ex = (HttpClientErrorException)exception;
					assertEquals(500, ex.getRawStatusCode());
				}
			}.perform();
		}			
}
