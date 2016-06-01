package org.axe.helper.mvc;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.axe.helper.Helper;
import org.axe.helper.ioc.ClassHelper;
import org.axe.interface_.mvc.Interceptor;
import org.axe.util.CollectionUtil;
import org.axe.util.ReflectionUtil;

/**
 * 拦截器 助手类
 * Created by CaiDongYu on 2016年5月30日 下午12:21:51.
 */
public final class InterceptorHelper implements Helper{

	private static Map<Class<? extends Interceptor>,Interceptor> INTERCEPTOR_MAP;//不保证顺序
	
	@Override
	public void init() {
		synchronized (this) {
			INTERCEPTOR_MAP = new HashMap<>();
			Set<Class<?>> interceptorClassSet = ClassHelper.getClassSetBySuper(Interceptor.class);
	        if(CollectionUtil.isNotEmpty(interceptorClassSet)){
	        	for(Class<?> interceptorClass:interceptorClassSet){
	        		Interceptor interceptor = ReflectionUtil.newInstance(interceptorClass);
	        		interceptor.init();//初始化Filter
	        		INTERCEPTOR_MAP.put(interceptor.getClass(), interceptor);
	        	}
	        }
		}
	}
	
	public static Map<Class<? extends Interceptor>, Interceptor> getInterceptorMap() {
		return INTERCEPTOR_MAP;
	}
	
}