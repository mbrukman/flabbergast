AC_INIT([flabbergast], [0.19], [andre@masella.name], [flabbergast], [http://flabbergast.org])
AC_CONFIG_SRCDIR([Makefile.am])
AC_CONFIG_AUX_DIR([build-aux])
AC_CONFIG_MACRO_DIR([m4])
AM_INIT_AUTOMAKE([1.11 foreign no-dependencies])
AM_MAINTAINER_MODE
AC_ARG_ENABLE(jvm, AC_HELP_STRING([--disable-jvm], [disable Java Virtual Machine support (default is autodetect)]))
AC_ARG_ENABLE(fatjar, AC_HELP_STRING([--enable-fatjar], [build a single JAR with all dependencies]))
AC_ARG_WITH(jars, AC_HELP_STRING([--with-jars], [path to Java librares required (default is /usr/share/java)]), [JAR_PATH="$withval"], [JAR_PATH=/usr/share/java])
AC_ARG_ENABLE(cli, AC_HELP_STRING([--disable-cli], [disable Mono/Common Language Infrastructure support (default is autodetect)]))

m4_ifdef([AM_SILENT_RULES], [AM_SILENT_RULES([yes])], )

# Checks for programs.
AC_PROG_LN_S

# JVM
METAINF_JAR="$JAR_PATH/metainf-services.jar"
AC_SUBST([METAINF_JAR])
JANSI_JAR="$JAR_PATH/jansi.jar"
AC_SUBST([JANSI_JAR])
JLINE_JAR="$JAR_PATH/jline2.jar"
AC_SUBST([JLINE_JAR])
ASM_JAR="$JAR_PATH/asm4.jar"
AC_SUBST([ASM_JAR])
ASM_COMMONS_JAR="$JAR_PATH/asm4-commons.jar"
AC_SUBST([ASM_COMMONS_JAR])
COMMONS_CLI_JAR="$JAR_PATH/commons-cli.jar"
AC_SUBST([COMMONS_CLI_JAR])
JSON_JAR="$JAR_PATH/javax.json.jar"
AC_SUBST([JSON_JAR])

if test "$enable_jvm" != no; then
	AC_CHECK_TOOL(AR, ar)
	AC_PATH_TOOL(JAVA, java, undefined)
	AC_PATH_TOOL(JAVAC, javac, undefined)
	AC_PATH_TOOL(JAR, jar, undefined)
	if test "$JAVA" = undefined -o "$JAVAC" = undefined -o "$JAR" = undefined; then
		target_java=no
	else
		AC_CHECK_FILES([${ASM_JAR} ${COMMONS_CLI_JAR} ${JLINE_JAR} ${JSON_JAR} ${METAINF_JAR}], [ target_java=yes ], [ target_java=no ])
	fi
else
	target_java=no
fi

if test "$enable_cli" != no; then
	AC_PATH_TOOL(MSBUILD, msbuild, undefined)
	if test "$MSBUILD" = undefined; then
		target_cli=no
	else
		target_cli=yes
	fi
else
	target_cli=no
fi

AM_CONDITIONAL([BUILD_CLI], [test x$target_cli = xyes])
AM_CONDITIONAL([BUILD_JVM], [test x$target_java = xyes])
AM_CONDITIONAL([BUILD_FATJAR], [test x$enable_fatjar = xyes])

AC_CONFIG_FILES([
	Makefile
	platform/cli/Makefile
	platform/cli/AssemblyInfo.cs
	platform/cli/config.cs
	platform/cli/flabbergast.nuspec
	platform/jvm/Makefile
	platform/jvm/runtime/flabbergast/Configuration.java
	vim/Makefile
]) 
AC_CONFIG_FILES([update-flabbergast:update-flabbergast.in], [chmod +x update-flabbergast])
AC_CONFIG_FILES([platform/cli/nuget-deploy:platform/cli/nuget-deploy.in], [chmod +x platform/cli/nuget-deploy])
AC_CONFIG_FILES([platform/jvm/maven-deploy:platform/jvm/maven-deploy.in], [chmod +x platform/jvm/maven-deploy])
AC_CONFIG_FILES([build-macos-pkg:build-macos-pkg.in], [chmod +x build-macos-pkg])
AC_OUTPUT

echo
echo Java Virtual Machine: $target_java
echo Commmon Language Infrastructure: $target_cli
