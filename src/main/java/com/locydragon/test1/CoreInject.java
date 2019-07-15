package com.locydragon.test1;

import javassist.*;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.net.URLDecoder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.ProtectionDomain;

/**
 * @author  LocyDragon
 */
public class CoreInject {
	public static final String craftPlayerClass = "CraftPlayer";
	public static final String obc = "org/bukkit/craftbukkit";
	public static final String version = "";
	public static void premain(String agentArgs, Instrumentation inst) {
		//注意 在这里使用Bukkit.getServer() 会返回null 因为服务器还没有启动.
		inst.addTransformer(new ClassFileTransformer() {
			@Override
			public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
				if (className.startsWith(obc) && className.endsWith(craftPlayerClass)) {
					ClassPool pool = ClassPool.getDefault();
					//获取Javassist的储存类的池
					try {
						Path path = Paths.get(Class.forName("org.bukkit.Bukkit").getProtectionDomain().getCodeSource().getLocation().toString().substring(6));
						//这样获取核心所在路径，这么做为了下面把核心导入ClassPool，不导入会找不到CraftPlayer类.
						String pathUtf = URLDecoder.decode(path.toFile().getPath(), "utf-8");
                        //路径转码 避免路径有中文而乱码报错
						pool.insertClassPath(pathUtf);
						pool.importPackage("java.lang.reflect.Field");
						pool.importPackage("java.lang.reflect.Method");
						pool.importPackage("org.bukkit.Bukkit");
						pool.importPackage("org.bukkit.entity.Player");
						pool.importPackage("org.bukkit.plugin.java.JavaPlugin");
						//导入类~
						CtClass ctClass = pool.getCtClass(className.replace("/", "."));
						//获取CraftPlayer类
						CtMethod sendMsgMethod = ctClass.getDeclaredMethod("sendMessage", new CtClass[]{ pool.getCtClass("java.lang.String") });
						//获取发送信息的方法
						ctClass.removeMethod(sendMsgMethod);
						sendMsgMethod.setName("sendMessage_impl");
						//修改名称 避免方法重复
						ctClass.addMethod(sendMsgMethod);
						CtMethod newMethod = CtNewMethod.copy(sendMsgMethod, "sendMessage", ctClass, null);
						//创建新的方法，复制原来的方法
						StringBuilder code = new StringBuilder();
						//使用一个StringBuilder来储存源码
						//下面就开始写注入的代码了
						code.append("{\n");
						code.append("try {");
						code.append("JavaPlugin plugin = Bukkit.getPluginManager().getPlugin(\"TestPlugin\");");
						code.append("Method method = plugin.getClass().getMethod(\"onCall\", new Class[] {Player.class, String.class});");
						code.append("method.invoke(null, new Object[]{(Player)this, $1});");
						code.append("} catch (Exception e) {}");
						//$1就是第一个参数的意思，这里就是sendMessage发送的信息.
						code.append("}\n");
						newMethod.insertBefore(code.toString());
						ctClass.addMethod(newMethod);
						return ctClass.toBytecode();
						//返回字节码
					} catch (ClassNotFoundException | NotFoundException | CannotCompileException | IOException exc) {
						exc.printStackTrace();
					}
				}
				return null;
				//需要return一个字节码对象，byte[]类型，如果没有要返回的请返回null.
			}
		});
	}

}
