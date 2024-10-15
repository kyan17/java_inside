package com.github.forax.framework.orm;

import org.h2.jdbcx.JdbcDataSource;

import javax.sql.DataSource;
import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.Serial;
import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.stream.Stream;

public final class ORM {

  // My code :

  private static final ThreadLocal<Connection> CONNECTION_LOCAL = new ThreadLocal<>();
  private static final String DEFAULT_TYPE = "VARCHAR(255)";

  public static void transaction(JdbcDataSource dataSource, TransactionBlock block) throws SQLException {
    Objects.requireNonNull(dataSource);
    Objects.requireNonNull(block);
    try (var connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      CONNECTION_LOCAL.set(connection);
      try {
        block.run();
      }  catch (Exception e) {
        connection.rollback();
        throw e;
      } finally {
        CONNECTION_LOCAL.remove();
      }
      connection.commit();
    } // connection.close();
  }

  private static boolean isPrimaryKey(PropertyDescriptor property) {
    var getter = property.getReadMethod();
    return getter != null && getter.isAnnotationPresent(Id.class);
  }

  static Connection currentConnection() {
    var connection =  CONNECTION_LOCAL.get();
    if (connection == null) throw new IllegalStateException("No transaction connection available");
    return connection;
  }

  static String findTableName(Class<?> beanClass) {
    var tableName = beanClass.getAnnotation(Table.class);
    var name = (tableName != null)? tableName.value() : beanClass.getSimpleName();
    return name.toUpperCase(Locale.ROOT);
  }

  static String findColumnName(PropertyDescriptor property) {
    var getter = property.getReadMethod();
    String name;
    if (getter != null) {
      var column = getter.getAnnotation(Column.class);
      name = (column != null)? column.value() : property.getName();
    } else name = property.getName();
    return name.toUpperCase(Locale.ROOT);
  }

  private static String setCorrectType(PropertyDescriptor property) {

  }

  public static void createTable(Class<?> beanClass) throws SQLException {
    Objects.requireNonNull(beanClass);
    var builder = new StringBuilder();
    builder.append("CREATE TABLE ").append(findTableName(beanClass)).append(" (\n");
    var beanInfo = Utils.beanInfo(beanClass);
    var separator = "";
    String primaryColumn = null;
    for (var property : beanInfo.getPropertyDescriptors()) {
      if (property.getName().equals("class")) continue;
      var column = findColumnName(property);
      if (isPrimaryKey(property)) primaryColumn = column;
      var type = TYPE_MAPPING.getOrDefault(property.getPropertyType(), DEFAULT_TYPE);
      builder.append(separator).append(column).append(' ').append(type);
      separator = ", \n";
    }
    if (primaryColumn != null)
      builder.append("PRIMARY KEY (").append(primaryColumn).append(")");
    builder.append(')');
    var query = builder.toString(); // Donnée importante, on la stocke dans un variable
    var connection = currentConnection();
    try (var statement = connection.createStatement()) {
      statement.execute(query);
    }
    connection.commit(); // Pas obligatoire, car les CREATE TABLE sont toujours en auto commit
  }

  // Forax Code :

  private ORM() {
    throw new AssertionError();
  }

  @FunctionalInterface
  public interface TransactionBlock {
    void run() throws SQLException;
  }

  private static final Map<Class<?>, String> TYPE_MAPPING = Map.of(
      int.class, "INTEGER",
      Integer.class, "INTEGER",
      long.class, "BIGINT",
      Long.class, "BIGINT",
      String.class, "VARCHAR(255)"
  );

  private static Class<?> findBeanTypeFromRepository(Class<?> repositoryType) {
    var repositorySupertype = Arrays.stream(repositoryType.getGenericInterfaces())
        .flatMap(superInterface -> {
          if (superInterface instanceof ParameterizedType parameterizedType
              && parameterizedType.getRawType() == Repository.class) {
            return Stream.of(parameterizedType);
          }
          return null;
        })
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("invalid repository interface " + repositoryType.getName()));
    var typeArgument = repositorySupertype.getActualTypeArguments()[0];
    if (typeArgument instanceof Class<?> beanType) {
      return beanType;
    }
    throw new IllegalArgumentException("invalid type argument " + typeArgument + " for repository interface " + repositoryType.getName());
  }

  private static class UncheckedSQLException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 42L;

    private UncheckedSQLException(SQLException cause) {
      super(cause);
    }

    @Override
    public SQLException getCause() {
      return (SQLException) super.getCause();
    }
  }

}
