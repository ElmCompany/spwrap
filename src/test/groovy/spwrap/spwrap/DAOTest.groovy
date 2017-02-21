package spwrap

import spock.lang.*

// integration test
@Unroll
class DAOTest extends Specification{

	def customerDao
	
	def setup() {
		System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "TRACE")
		TestUtils.install()
		
		customerDao = new DAO.Builder(TestUtils.ds).build().create(CustomerDAO.class)
	}
	
	def cleanup() {
		TestUtils.rollback()
	}
	
	def "using the @Param to pass input parameters"(){
		given:
			def firstName = "Abdullah"
			def lastName = "Mohammad"
		
		when:
			customerDao.createCustomer0(firstName, lastName)
			
		then:
			noExceptionThrown()
	}

	def "using @Scalar annotation to map single output parameter"(){
		given:
			def firstName = "Abdullah"
			def lastName = "Mohammad"
		
		when:
			def custId = customerDao.createCustomer(firstName, lastName)		
		then:
			custId == 0
	}
	
	def "using @Scalar annotation to map single output parameter - invoking the Stored Proc multiple times"(){
		given:
			def firstName = "Abdullah"
			def lastName = "Mohammad"
		
		when:
			def custId0 = customerDao.createCustomer(firstName, lastName)		
			def custId1 = customerDao.createCustomer(firstName, lastName)		
			def custId2 = customerDao.createCustomer(firstName, lastName)		
			def custId3 = customerDao.createCustomer(firstName, lastName)		
		then:
			custId0 == 0
			custId1 == 1
			custId2 == 2
			custId3 == 3
	}
	
	def "using return type as a mapper for output parameters"(){
		given:
			def firstName = "Abdullah"
			def lastName = "Mohammad"
		
		when:
			customerDao.createCustomer0(firstName, lastName)
			def customer = customerDao.getCustomer2(0);
			
		then:
			customer.firstName() == firstName
			customer.lastName() == lastName
	}
	
	
	def "override the return type Mapper using a custom mapping through the annotation @Mapper"(){
		given:
			def firstName = "Abdullah"
			def lastName = "Mohammad"
		
		when:
			customerDao.createCustomer0(firstName, lastName)
			def customer = customerDao.getCustomer1(0);
			
		then:
			customer.firstName() == firstName + ".CustomParamsMapper"
			customer.lastName() == lastName + ".CustomParamsMapper"
	}
	
	
	def "Mappers (through return type -implicit- or @Mapper - explicit- ) will override @Scalar annotation"(){
		given:
			def firstName = "Farida"
			def lastName = "Mohammad"
			
		when:
			customerDao.createCustomer0(firstName, lastName)
			def customer = customerDao.getCustomer3(0);
		
		then:
			customer.firstName() == firstName
			customer.lastName() == lastName
	}
	
	
	def "@Scalar annotation will cause CallException if no mappers, and the stored proc return more than 1 out params"(){
		given:
			def firstName = "Farida"
			def lastName = "Mohammad"
			
		when:
			customerDao.createCustomer0(firstName, lastName)
			def customer = customerDao.getCustomer4(0);
		
		then:
			thrown(CallException)
	}
	
	def "only 2 explicit Mappers are allowed"(){
		given:
			def firstName = "Farida"
			def lastName = "Mohammad"
			
		when:
			customerDao.createCustomer0(firstName, lastName)
			def customer = customerDao.getCustomer5(0);
		
		then:
			thrown(CallException)
	}
	
	
	def "only 2 explicit Mappers are allowed, one of each type (ResultSetMapper and TypedOutputParamMapper)"(){
		given:
			def firstName = "Farida"
			def lastName = "Mohammad"
			
		when:
			customerDao.createCustomer0(firstName, lastName)
			def customer = customerDao.getCustomer6(0);
		
		then:
			thrown(CallException)
	}
	
	def "using implicit ResultSet Mapper to map result set"(){
	
		given:
			def firstName1 = "Farida"
			def lastName1 = "Mohammad"
			def firstName2 = "Abdullah"
			def lastName2 = "Mohammad"
			
		when:
			customerDao.createCustomer0(firstName1, lastName1)
			customerDao.createCustomer0(firstName2, lastName2)
			def list = customerDao.listCustomers2();
			
		then:
			2 == list.size()
			with(list.get(0)){
				firstName1 == firstName()
				lastName1 == lastName()
			}
			
			with(list.get(1)){
				firstName2 == firstName()
				lastName2 == lastName()
			}
	}
	
	def "using explicit ResultSet Mapper to map result set"(){
	
		given:
			def firstName1 = "Farida"
			def lastName1 = "Mohammad"
			def firstName2 = "Abdullah"
			def lastName2 = "Mohammad"
			
		when:
			customerDao.createCustomer0(firstName1, lastName1)
			customerDao.createCustomer0(firstName2, lastName2)
			def list = customerDao.listCustomers1();
			
		then:
			2 == list.size()
			with(list.get(0)){
				firstName1 == firstName()
				lastName1 == lastName()
			}
			
			with(list.get(1)){
				firstName2 == firstName()
				lastName2 == lastName()
			}
	}
	
	
	def "returning a Tuple as a result of stored proc return both result set and output parameters" (){
		
		given:
			def firstName1 = "Farida"
			def lastName1 = "Mohammad"
			def firstName2 = "Abdullah"
			def lastName2 = "Mohammad"
			
		when:
			customerDao.createCustomer0(firstName1, lastName1)
			customerDao.createCustomer0(firstName2, lastName2)
			def tuple = customerDao.listCustomersWithDate();
			
		then:
			tuple.object != null
			
			2 == tuple.list.size()
			with(tuple.list.get(0)){
				firstName1 == firstName()
				lastName1 == lastName()
			}
			
			with(tuple.list.get(1)){
				firstName2 == firstName()
				lastName2 == lastName()
			}
	}
	
	def "StoredProc annotation will use method name as stored procedure name if no one is specified" (){
	
		given:
			def firstName1 = "Farida"
			def lastName1 = "Mohammad"
			def firstName2 = "Abdullah"
			def lastName2 = "Mohammad"
			
		when:
			customerDao.createCustomer0(firstName1, lastName1)
			customerDao.createCustomer0(firstName2, lastName2)
			def tuple = customerDao.list_customers_with_date();
			
		then:
			tuple.object != null
			
			2 == tuple.list.size()
			with(tuple.list.get(0)){
				firstName1 == firstName()
				lastName1 == lastName()
			}
			
			with(tuple.list.get(1)){
				firstName2 == firstName()
				lastName2 == lastName()
			}
	}
	
	def "stored procedure will return error code from database" (){
		
		when:
			customerDao.callStoredProcWithError();
		then:
			def e = thrown(CallException)
			e.message.startsWith("Stored Procedure returns error code 1")
			e.errorCode == 1
	}
	
	def "choose to disable the feature of result code and message, only we care about business related parameters" (){
		given:
			def dao = new DAO.Builder(TestUtils.ds).config(new Config().useStatusFields(false)).build()
			def customerDAO2 = dao.create(CustomerDAO.class)
			
		when:
			customerDAO2.getFirstTableNameNoResultFields()

		then:
			noExceptionThrown()
		
	}
	
	def "we can even change the success value in the default result fields" (){
		given:
			def dao = new DAO.Builder(TestUtils.ds).config(new Config().successCode((short) 1)).build()
			def customerDAO2 = dao.create(CustomerDAO.class);
		
		when:
			customerDAO2.callStoredProcWithError()
			
		then:
			noExceptionThrown()
	}
	
	def "yet another test case for result set mapper" (){
		when:
			def listTables = customerDao.listTables();
			
		then:
			listTables.size() > 2
			listTables.contains("ROUTINES")
			listTables.contains("CUSTOMERS")
	}
	
	def "you can use @Scalar with Output parameters only mapper" (){
		when:
			def listTables = customerDao.listTables2();
			
		then:
			thrown(CallException)
	}

}