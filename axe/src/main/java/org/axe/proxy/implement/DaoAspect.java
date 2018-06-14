/**
 * MIT License
 * 
 * Copyright (c) 2017 CaiDongyu
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.axe.proxy.implement;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.axe.annotation.aop.Aspect;
import org.axe.annotation.persistence.Dao;
import org.axe.annotation.persistence.ResultProxy;
import org.axe.annotation.persistence.Sql;
import org.axe.bean.persistence.Page;
import org.axe.bean.persistence.PageConfig;
import org.axe.helper.persistence.DataBaseHelper;
import org.axe.helper.persistence.DataSourceHelper;
import org.axe.interface_.persistence.BaseRepository;
import org.axe.interface_.persistence.SqlResultProxy;
import org.axe.interface_.proxy.Proxy;
import org.axe.proxy.base.ProxyChain;
import org.axe.util.CastUtil;
import org.axe.util.CollectionUtil;
import org.axe.util.ReflectionUtil;
import org.axe.util.StringUtil;
import org.axe.util.sql.CommonSqlUtil;
import org.axe.util.sql.MySqlUtil;
//import org.axe.util.sql.MySqlUtil;
import org.axe.util.sql.OracleUtil;

/**
 * Dao代理 代理所有 @Dao注解的接口
 * 
 * @author CaiDongyu on 2016/4/19.
 */
@Aspect(Dao.class)
public class DaoAspect implements Proxy {

	@Override
	public Object doProxy(ProxyChain proxyChain) throws Throwable {
		Object result = null;
		Method targetMethod = proxyChain.getTargetMethod();
		Object[] methodParams = proxyChain.getMethodParams();
		Class<?>[] parameterTypes = targetMethod.getParameterTypes();
		Class<?> daoClass = proxyChain.getTargetClass();
		String dataSourceName = daoClass.getAnnotation(Dao.class).dataSource();
		if(StringUtil.isEmpty(dataSourceName)){
			dataSourceName = DataSourceHelper.getDefaultDataSourceName();
		}

		// 如果有sql结果代理器
		Type returnType = null;
		Class<?> rawType = null;
		SqlResultProxy sqlResultProxy = null;
		if (targetMethod.isAnnotationPresent(ResultProxy.class)) {
			ResultProxy resultProxy = targetMethod.getAnnotation(ResultProxy.class);
			Class<? extends SqlResultProxy> proxyClass = resultProxy.value();
			returnType = resultProxy.returnType();// 伪造返回结果类型，这样查询就都是List<Map<String,Object>>结果集
			rawType = resultProxy.rawType();// 伪造返回结果类型，这样查询就都是List<Map<String,Object>>结果集
			sqlResultProxy = ReflectionUtil.newInstance(proxyClass);
		}

		if (targetMethod.isAnnotationPresent(Sql.class)) {
			Sql sqlAnnotation = targetMethod.getAnnotation(Sql.class);
			String sql = sqlAnnotation.value();

			// #解析指令代码
			sql = CommonSqlUtil.convertSqlAppendCommand(sql, methodParams);
			if(DataSourceHelper.isMySql(dataSourceName)){
				// #解析Sql中的类名字段名
				String[] sqlAndDataSourceName = CommonSqlUtil.convertHql2Sql(MySqlUtil.MYSQL_KEYWORD,sql);
				sql = sqlAndDataSourceName[0];
			}else if(DataSourceHelper.isOracle(dataSourceName)){
				// #解析Sql中的类名字段名
				String[] sqlAndDataSourceName = CommonSqlUtil.convertHql2Sql(OracleUtil.ORACLE_KEYWORD,sql);
				sql = sqlAndDataSourceName[0];
			}
			
			// #空格格式化，去掉首位空格，规范中间的空格{
			sql = sql.trim();
			while (sql.contains("  ")) {
				sql = sql.replaceAll("  ", " ");
			}
			sql = sql.trim();
			// }

			String sqlUpperCase = sql.toUpperCase();
			if (sqlUpperCase.startsWith("SELECT")) {
				returnType = returnType == null ? targetMethod.getGenericReturnType() : returnType;
				rawType = rawType == null ? targetMethod.getReturnType() : rawType;
				if (returnType instanceof ParameterizedType) {
					Type[] actualTypes = ((ParameterizedType) returnType).getActualTypeArguments();
					// 带泛型的，只支持Page、List、Map这样
					if (Page.class.isAssignableFrom(rawType) || // 如果要求返回类型是Page分页
							ReflectionUtil.compareType(List.class, rawType)) {
						
						result = listResult(actualTypes[0], dataSourceName, sql, methodParams, parameterTypes);

						if (Page.class.isAssignableFrom(rawType)) {
							// 如果是分页，包装返回结果
							result = pageResult(sql, methodParams, parameterTypes, (List<?>) result, dataSourceName);
						}
					} else if (ReflectionUtil.compareType(Map.class, rawType)) {
						// Map无所谓里面的泛型
						if (StringUtil.isEmpty(dataSourceName)) {
							result = DataBaseHelper.queryMap(sql, methodParams, parameterTypes);
						} else {
							result = DataBaseHelper.queryMap(sql, methodParams, parameterTypes, dataSourceName);
						}
					}
				} else {
					if (Page.class.isAssignableFrom(rawType) || ReflectionUtil.compareType(List.class, rawType)) {
						// Page、List
						/*if (StringUtil.isEmpty(dataSourceName)) {
							result = DataBaseHelper.queryList(sql, methodParams, parameterTypes);
						} else {
							result = DataBaseHelper.queryList(sql, methodParams, parameterTypes, dataSourceName);
						}*/
						result = listResult(returnType, dataSourceName, sql, methodParams, parameterTypes);

						if (Page.class.isAssignableFrom(rawType)) {
							// 如果是分页，包装返回结果
							result = pageResult(sql, methodParams, parameterTypes, (List<?>) result, dataSourceName);
						}
					} else if (ReflectionUtil.compareType(Map.class, rawType)) {
						// Map
						if (StringUtil.isEmpty(dataSourceName)) {
							result = DataBaseHelper.queryMap(sql, methodParams, parameterTypes);
						} else {
							result = DataBaseHelper.queryMap(sql, methodParams, parameterTypes, dataSourceName);
						}
					} else if (ReflectionUtil.compareType(Object.class, rawType)) {
						// Object
						if (StringUtil.isEmpty(dataSourceName)) {
							result = DataBaseHelper.queryMap(sql, methodParams, parameterTypes);
						} else {
							result = DataBaseHelper.queryMap(sql, methodParams, parameterTypes, dataSourceName);
						}
					} else if (ReflectionUtil.compareType(String.class, rawType)
							|| ReflectionUtil.compareType(Byte.class, rawType)
							|| ReflectionUtil.compareType(Boolean.class, rawType)
							|| ReflectionUtil.compareType(Short.class, rawType)
							|| ReflectionUtil.compareType(Character.class, rawType)
							|| ReflectionUtil.compareType(Integer.class, rawType)
							|| ReflectionUtil.compareType(Long.class, rawType)
							|| ReflectionUtil.compareType(Float.class, rawType)
							|| ReflectionUtil.compareType(Double.class, rawType)) {
						// String
						result = getBasetypeOrDate(sql, methodParams, parameterTypes, dataSourceName);
						result = CastUtil.castType(result, rawType);
					} else if ((rawType).isPrimitive()) {
						if (ReflectionUtil.compareType(void.class, rawType)) {
							// void
							if (StringUtil.isEmpty(dataSourceName)) {
								result = DataBaseHelper.queryList(sql, methodParams, parameterTypes);
							} else {
								result = DataBaseHelper.queryList(sql, methodParams, parameterTypes, dataSourceName);
							}
						} else {
							// 基本类型
							if (StringUtil.isEmpty(dataSourceName)) {
								result = DataBaseHelper.queryPrimitive(sql, methodParams, parameterTypes);
							} else {
								result = DataBaseHelper.queryPrimitive(sql, methodParams, parameterTypes, dataSourceName);
							}
						}
					} else {
						// Entity
						if (StringUtil.isEmpty(dataSourceName)) {
							result = DataBaseHelper.queryEntity(rawType, sql, methodParams, parameterTypes);
						} else {
							result = DataBaseHelper.queryEntity(rawType, sql, methodParams, parameterTypes, dataSourceName);
						}
					}
				}
			} else {
				if (StringUtil.isEmpty(dataSourceName)) {
					result = DataBaseHelper.executeUpdate(sql, methodParams, parameterTypes);
				} else {
					result = DataBaseHelper.executeUpdate(sql, methodParams, parameterTypes, dataSourceName);
				}
			}
		} else if (BaseRepository.class.isAssignableFrom(daoClass)
				&& !ReflectionUtil.compareType(BaseRepository.class, daoClass)) {
			String methodName = targetMethod.getName();
			Class<?>[] paramAry = targetMethod.getParameterTypes();
			if ("insertEntity".equals(methodName)) {
				// # Repository.insertEntity(Object entity);
				if (paramAry.length == 1 && ReflectionUtil.compareType(paramAry[0], Object.class)) {
					Object entity = methodParams[0];
					if (StringUtil.isEmpty(dataSourceName)) {
						result = DataBaseHelper.insertEntity(entity);
					} else {
						result = DataBaseHelper.insertEntity(entity, dataSourceName);
					}
				}
			} else if ("deleteEntity".equals(methodName)) {
				// # Repository.deleteEntity(Object entity);
				if (paramAry.length == 1 && ReflectionUtil.compareType(paramAry[0], Object.class)) {
					Object entity = methodParams[0];
					if (StringUtil.isEmpty(dataSourceName)) {
						result = DataBaseHelper.deleteEntity(entity);
					} else {
						result = DataBaseHelper.deleteEntity(entity, dataSourceName);
					}
				}
			} else if ("updateEntity".equals(methodName)) {
				// # Repository.updateEntity(Object entity);
				if (paramAry.length == 1 && ReflectionUtil.compareType(paramAry[0], Object.class)) {
					Object entity = methodParams[0];
					if (StringUtil.isEmpty(dataSourceName)) {
						result = DataBaseHelper.updateEntity(entity);
					} else {
						result = DataBaseHelper.updateEntity(entity, dataSourceName);
					}
				}
			} else if ("getEntity".equals(methodName)) {
				// # Repository.getEntity(T entity);
				if (paramAry.length == 1 && ReflectionUtil.compareType(paramAry[0], Object.class)) {
					Object entity = methodParams[0];
					if (StringUtil.isEmpty(dataSourceName)) {
						result = DataBaseHelper.getEntity(entity);
					} else {
						result = DataBaseHelper.getEntity(entity, dataSourceName);
					}
				}
			} else if ("saveEntity".equals(methodName)) {
				// # Repository.saveEntity(Object entity);
				if (paramAry.length == 1 && ReflectionUtil.compareType(paramAry[0], Object.class)) {
					Object entity = methodParams[0];
					if (StringUtil.isEmpty(dataSourceName)) {
						result = DataBaseHelper.insertOnDuplicateKeyEntity(entity);
					} else {
						result = DataBaseHelper.insertOnDuplicateKeyEntity(entity, dataSourceName);
					}
				}
			} else {
				result = proxyChain.doProxyChain();
			}
		} else {
			result = proxyChain.doProxyChain();
		}

		// 如果有Sql结果代理器，那么代理一下
		if (sqlResultProxy != null) {
			result = sqlResultProxy.proxy(result);
		}
		return result;
	}

	private Object getBasetypeOrDate(String sql, Object[] methodParams, Class<?>[] parameterTypes,
			String dataSourceName) throws SQLException {
		// Date
		Map<String, Object> resultMap = DataBaseHelper.queryMap(sql, methodParams, parameterTypes, dataSourceName);
		do {
			if (resultMap == null)
				break;
			if (CollectionUtil.isEmpty(resultMap))
				break;

			Set<String> keySet = resultMap.keySet();
			return resultMap.get(keySet.iterator().next());
		} while (false);
		return null;
	}

	private Object getBasetypeOrDateList(String sql, Object[] methodParams, Class<?>[] parameterTypes,
			String dataSourceName) throws SQLException {
		List<Map<String, Object>> resultList = null;
		if (StringUtil.isEmpty(dataSourceName)) {
			resultList = DataBaseHelper.queryList(sql, methodParams, parameterTypes);
		} else {
			resultList = DataBaseHelper.queryList(sql, methodParams, parameterTypes, dataSourceName);
		}
		List<Object> list = new ArrayList<Object>();
		if (CollectionUtil.isNotEmpty(resultList)) {
			for (Map<String, Object> row : resultList) {
				if (row.size() == 1) {
					list.add(row.entrySet().iterator().next().getValue());
				}
			}
		}
		return list;
	}
	

	private Object listResult(Type returnType, String dataSourceName, String sql, Object[] methodParams, Class<?>[] parameterTypes) throws SQLException{
		Object result = null;
		if (returnType instanceof ParameterizedType) {
			// List<Map<String,Object>>
			if (ReflectionUtil.compareType(Map.class,
					(Class<?>) ((ParameterizedType) returnType).getRawType())) {
				if (StringUtil.isEmpty(dataSourceName)) {
					result = DataBaseHelper.queryList(sql, methodParams, parameterTypes);
				} else {
					result = DataBaseHelper.queryList(sql, methodParams, parameterTypes,
							dataSourceName);
				}
			}
		} else if (returnType instanceof WildcardType) {
			// List<?>
			if (StringUtil.isEmpty(dataSourceName)) {
				result = DataBaseHelper.queryList(sql, methodParams, parameterTypes);
			} else {
				result = DataBaseHelper.queryList(sql, methodParams, parameterTypes,
						dataSourceName);
			}
		} else {
			if (ReflectionUtil.compareType(Object.class, (Class<?>) returnType)) {
				// List<Object>
				if (StringUtil.isEmpty(dataSourceName)) {
					result = DataBaseHelper.queryList(sql, methodParams, parameterTypes);
				} else {
					result = DataBaseHelper.queryList(sql, methodParams, parameterTypes,
							dataSourceName);
				}
			} else if (ReflectionUtil.compareType(Map.class, (Class<?>) returnType)) {
				// List<Map>
				if (StringUtil.isEmpty(dataSourceName)) {
					result = DataBaseHelper.queryList(sql, methodParams, parameterTypes);
				} else {
					result = DataBaseHelper.queryList(sql, methodParams, parameterTypes,
							dataSourceName);
				}
			} else if (ReflectionUtil.compareType(String.class, (Class<?>) returnType)
					|| ReflectionUtil.compareType(Date.class, (Class<?>) returnType)
					|| ReflectionUtil.compareType(Byte.class, (Class<?>) returnType)
					|| ReflectionUtil.compareType(Boolean.class, (Class<?>) returnType)
					|| ReflectionUtil.compareType(Short.class, (Class<?>) returnType)
					|| ReflectionUtil.compareType(Character.class, (Class<?>) returnType)
					|| ReflectionUtil.compareType(Integer.class, (Class<?>) returnType)
					|| ReflectionUtil.compareType(Long.class, (Class<?>) returnType)
					|| ReflectionUtil.compareType(Float.class, (Class<?>) returnType)
					|| ReflectionUtil.compareType(Double.class, (Class<?>) returnType)) {
				// List<String>
				result = getBasetypeOrDateList(sql, methodParams, parameterTypes, dataSourceName);
			} else {
				// Entity
				if (StringUtil.isEmpty(dataSourceName)) {
					result = DataBaseHelper.queryEntityList((Class<?>) returnType, sql, methodParams,
							parameterTypes);
				} else {
					result = DataBaseHelper.queryEntityList((Class<?>) returnType, sql, methodParams,
							parameterTypes, dataSourceName);
				}
			}
		}
		
		return result;
	}

	/**
	 * 包装List返回结果成分页
	 */
	private <T> Page<T> pageResult(String sql, Object[] params, Class<?>[] paramTypes, List<T> records,
			String dataSourceName) {
		
		PageConfig pageConfig = CommonSqlUtil.getPageConfigFromParams(params, paramTypes);
		Object[] params_ = new Object[params.length - 1];
		for (int i = 0; i < params_.length; i++) {
			params_[i] = params[i];
		}
		Class<?>[] paramTypes_ = new Class<?>[paramTypes.length - 1];
		for (int i = 0; i < paramTypes_.length; i++) {
			paramTypes_[i] = paramTypes[i];
		}
		long count = 0;
		if (StringUtil.isEmpty(dataSourceName)) {
			count = DataBaseHelper.countQuery(sql, params_, paramTypes_);
		} else {
			count = DataBaseHelper.countQuery(sql, params_, paramTypes_, dataSourceName);
		}
		pageConfig = pageConfig == null ? new PageConfig(1, count) : pageConfig;
		long pages = count / pageConfig.getPageSize();
		if (pages * pageConfig.getPageSize() < count)
			pages++;

		return new Page<>(records, pageConfig, count, pages);
	}
	
}
