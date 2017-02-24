package spwrap;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spwrap.db.Database;
import spwrap.db.GenericDatabase;
import spwrap.mappers.OutputParamMapper;
import spwrap.mappers.ResultSetMapper;
import spwrap.result.Result;

/**
 * Execute Stored Procedures read the wiki and github README for more
 * information
 * 
 * @author mhewedy
 *
 */
public class Caller {

	private static Logger log = LoggerFactory.getLogger(Caller.class);

	private static final int JDBC_PARAM_OFFSIT = 1;
	private static final int NUM_OF_STATUS_FIELDS = 2;

	private Config config = new Config();

	private final DataSource dataSource;

	private final String jdbcUrl;
	private final String username;
	private final String password;

	Caller(DataSource dataSource) {
		this.jdbcUrl = null;
		this.username = null;
		this.password = null;
		this.dataSource = dataSource;
	}

	/**
	 * Use {@link #Caller(DataSource)} when possible
	 *
	 * @param jdbcUrl
	 * @param username
	 * @param password
	 */
	Caller(String jdbcUrl, String username, String password) {
		super();
		this.dataSource = null;
		this.jdbcUrl = jdbcUrl;
		this.username = username;
		this.password = password;
	}
	
	void setConfig(Config config) {
		this.config = config;
	}

	/**
	 * execute SP with input parameters and result list of result from result
	 * 
	 * @param procName
	 * @param inParams
	 * @param rsMapper
	 * @return
     * @see {@link #call(String, java.util.List, java.util.List, OutputParamMapper, ResultSetMapper)}
	 */
	public final <T> List<T> call(String procName, List<Param> inParams, ResultSetMapper<T> rsMapper) {
		return call(procName, inParams, null, null, rsMapper).list();
	}

	/**
	 * execute SP without input parameters and with result list
	 * 
	 * @param procName
	 * @param rsMapper
	 * @return
	 */
	public final <T> List<T> call(String procName, ResultSetMapper<T> rsMapper) {
		return call(procName, null, rsMapper);
	}

	/**
	 * execute SP with list of input parameters without returning any result
	 * 
	 * @param procName
	 * @param inParams
	 * @return
     * @see {@link #call(String, java.util.List, java.util.List, OutputParamMapper, ResultSetMapper)}
	 */
	public final void call(String procName, List<Param> inParams) {
		call(procName, inParams, null, null);
	}

	/**
	 * execute SP with no input nor output parameters
	 * 
	 * @param procName
	 * @return
     * @see {@link #call(String, java.util.List, java.util.List, OutputParamMapper, ResultSetMapper)}
	 */
	public final void call(String procName) {
		call(procName, null, null, null);
	}

	/**
	 * execute SP with no input parameters and return output parameters
	 * 
	 * @param procName
	 * @param outParamsTypes
	 *            (don't add types for result code and result message)
	 * @param paramMapper
	 * @return
	 * @see {@link #call(String, java.util.List, java.util.List, OutputParamMapper, ResultSetMapper)}
	 */
	public final <T> T call(String procName, List<ParamType> outParamsTypes, OutputParamMapper<T> paramMapper) {
		return call(procName, null, outParamsTypes, paramMapper);
	}

	/**
	 * execute SP with list of input parameters and return list of output
	 * parameters
	 * 
	 * @param procName
	 * @param inParams
	 * @param outParamsTypes
	 *            (don't add types for result code and result message)
	 * @param paramMapper
	 * @return
     * @see {@link #call(String, java.util.List, java.util.List, OutputParamMapper, ResultSetMapper)}
	 */
	public final <T> T call(String procName, List<Param> inParams, List<ParamType> outParamsTypes,
			OutputParamMapper<T> paramMapper) {
		return call(procName, inParams, outParamsTypes, paramMapper, null).object();
	}

	/**
	 * Method that takes SP call string {call SP(?, ...)} and additional -
	 * optional - parameters (input parameters, output parameters, output
	 * parameters mapping function and result set mapping function), and execute
	 * the SP with input parameters (if not null) and return the output
	 * parameters through the output parameters mapping function (if both output
	 * parameter types and output parameter mapping functions are not null) and
	 * if there's a result set and the result set mapping function is not null,
	 * then it returns a list of results.
	 * 
	 * <br />
	 * <br />
	 * All other execute methods in this class are just a shortcut version of
	 * this method
	 * 
	 * @param procName
	 * @param inParams
	 * @param outParamsTypes
	 * @param paramMapper
	 * @param rsMapper
	 * @return
	 */
	public final <T, U> Tuple<T, U> call(String procName, List<Param> inParams, List<ParamType> outParamsTypes,
			OutputParamMapper<U> paramMapper, ResultSetMapper<T> rsMapper) {

		final long startTime = System.currentTimeMillis();

		Connection con = null;
		CallableStatement call = null;
		ResultSet rs = null;

		final String callableStmt = Util.createCallableString(procName,
				(config.useStatusFields() ? NUM_OF_STATUS_FIELDS : 0)
						+ (inParams != null ? inParams.size() : 0)
						+ (outParamsTypes != null ? outParamsTypes.size() : 0));

		Tuple<T, U> result = null;
		try {

			if (dataSource != null) {
				con = dataSource.getConnection();
			} else {
				con = DriverManager.getConnection(jdbcUrl, username, password);
			}

			call = con.prepareCall(callableStmt);

			int startOfOutParamCnt = inParams != null ? inParams.size() : 0;

			if (inParams != null) {
				for (int i = 0; i < inParams.size(); i++) {
					int jdbcParamId = i + JDBC_PARAM_OFFSIT;
					call.setObject(jdbcParamId, inParams.get(i).value, inParams.get(i).sqlType);
				}
			}

			if (outParamsTypes != null) {
				log.debug("setting input parameters");
				for (int i = 0; i < outParamsTypes.size(); i++) {
					call.registerOutParameter(i + startOfOutParamCnt + JDBC_PARAM_OFFSIT,
							outParamsTypes.get(i).sqlType);
				}
			}

			int resultCodeIndex = (inParams != null ? inParams.size() : 0)
					+ (outParamsTypes != null ? outParamsTypes.size() : 0) + 1;
			if (config.useStatusFields()) {
				call.registerOutParameter(resultCodeIndex, Types.BOOLEAN); // RESULT_CODE
				call.registerOutParameter(resultCodeIndex + 1, Types.VARCHAR); // RESULT_MSG
			}
			
			Database database = GenericDatabase.from(con);
			boolean hasResult = database.executeCall(call);

			List<T> list = null;
			if (hasResult && rsMapper != null) {
				list = new ArrayList<T>();
				log.debug("reading result set");
				rs = call.getResultSet();
				while (rs.next()) {
					list.add(rsMapper.map(Result.of(rs, null, -1)));
				}
			}

			U object = null;
			if (outParamsTypes != null) {
				log.debug("reading output parameters");
                for (ParamType ignored : outParamsTypes) {
                    object = paramMapper.map(Result.of(null, call, startOfOutParamCnt));
                }
			}

			if (config.useStatusFields()) {
				short resultCode = call.getShort(resultCodeIndex);
				if (resultCode != config.successCode()) {
					String resultMsg = call.getString(resultCodeIndex + 1);
					throw new CallException(resultCode, resultMsg);
				}
			}

			result = new Tuple<T, U>(list, object);

		} catch (CallException ex) {
			throw ex;
		} catch (Exception ex) {
			log.error("[" + callableStmt + "]", ex.getMessage());
			throw new CallException(ex.getMessage(), ex);
		} finally {
            logCall(startTime, callableStmt, inParams, outParamsTypes, result);
            Util.closeDBObjects(con, call, rs);
        }
		return result;
	}

    private <T, U> void logCall(long startTime, String callableStmt, List<Param> inParams, List<ParamType> outParamsTypes, Tuple<T, U> result) {
        if (log.isDebugEnabled()) {
            log.debug("\n>call sp: [{}] \n>\tIN Params: {}, \n>\tOUT Params Types: {}\n>\tResult: {}; \n>>\ttook: {} ms", callableStmt,
                    inParams != null ? Arrays.toString(inParams.toArray()) : "null",
                    outParamsTypes != null ? Arrays.toString(outParamsTypes.toArray()) : "null", result,
                    (System.currentTimeMillis() - startTime));
        } else {
            log.info(">call sp: [{}] took: {} ms", callableStmt, (System.currentTimeMillis() - startTime));
        }
    }

    // -------------- Classes and factory functions for dealing with Caller

	public static class ParamType {
		protected int sqlType;

		public static ParamType of(int sqlType) {
			ParamType p = new ParamType();
			p.sqlType = sqlType;
			return p;
		}

		@Override
		public String toString() {
			return Util.getAsString(sqlType);
		}
	}

	public static class Param extends ParamType {
		private Object value;

		public static Param of(Object value, int sqlType) {
			Param p = new Param();
			p.value = value;
			p.sqlType = sqlType;
			return p;
		}

		@Override
		public String toString() {
			return "[value=" + value + ", type=" + Util.getAsString(sqlType) + "]";
		}
	}

	public static List<ParamType> paramTypes(int... sqlTypes) {
		List<ParamType> pts = new ArrayList<ParamType>();
		for (int type : sqlTypes) {
			ParamType pt = new ParamType();
			pt.sqlType = type;
			pts.add(pt);
		}
		return pts;
	}

	public static List<Param> params(Param... params) {
		List<Param> pm = new ArrayList<Param>();
		for (Param param : params) {
			pm.add(param);
		}
		return pm;
	}
}
