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
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.nCopies;

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
      } catch (UncheckedSQLException e) {
        connection.rollback();
        throw e.getCause();
      } catch (Exception e) {
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
    } else {
      name = property.getName();
    }
    return name.toUpperCase(Locale.ROOT);
  }

  private static String createTableQuery(Class<?> beanType) {
    var beanInfo = Utils.beanInfo(beanType);
    var joiner = new StringJoiner(",\n", "(\n", "\n)");
    for (var property: beanInfo.getPropertyDescriptors()) {
      var propertyName = property.getName();
      if (propertyName.equals("class")) continue;
      var columnName = findColumnName(property);
      var propertyType = property.getPropertyType();
      var typeName = TYPE_MAPPING.get(propertyType);
      if (typeName == null)
        throw new UnsupportedOperationException("unknown type mapping for type " + propertyType.getName());
      var nullable = (propertyType.isPrimitive())? " NOT NULL" : "";
      var getter = property.getReadMethod();
      var autoincrement = (getter.isAnnotationPresent(GeneratedValue.class))? " AUTO_INCREMENT" : "";
      joiner.add(columnName + ' ' + typeName + nullable + autoincrement);
      if (getter.isAnnotationPresent(Id.class)) joiner.add("PRIMARY KEY (" + columnName + ')');
    }
    var tableName = findTableName(beanType);
    return "CREATE TABLE " + tableName + joiner + ";";
  }

  public static void createTable(Class<?> beanType) throws SQLException {
    var sqlQuery = createTableQuery(beanType);
    var connection = currentConnection();
    try (var statement = connection.createStatement()) {
      statement.executeUpdate(sqlQuery);
    }
    connection.commit(); // Pas obligatoire, car la création de table est toujours en auto commit
  }

  static Object toEntityClass(ResultSet resultSet, BeanInfo beanInfo, Constructor<?> constructor) throws SQLException {
    var instance = Utils.newInstance(constructor);
    for( var property: beanInfo.getPropertyDescriptors()) {
      var propertyName = property.getName();
      if (propertyName.equals("class")) continue;
      var value = resultSet.getObject(propertyName);
      Utils.invokeMethod(instance, property.getWriteMethod(), value);
    }
    return instance;
  }

  static List<?> findAll(
    Connection connection, String sqlQuery, BeanInfo beanInfo, Constructor<?> constructor, Object... args
  ) throws SQLException {
    var list = new ArrayList<>();
    try (var statement = connection.prepareStatement(sqlQuery)) {
      if (args != null) {
        for (var i = 0; i < args.length; i++) {
          statement.setObject(i + 1, args[i]);
        }
      }
      try (var resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          var bean = toEntityClass(resultSet, beanInfo, constructor);
          list.add(bean);
        }
      }
    }
    return list;
  }

  static PropertyDescriptor findIdProperty(BeanInfo beanInfo) {
    return Arrays.stream(beanInfo.getPropertyDescriptors())
      .filter(property -> property.getName().equals("id"))
      .findFirst()
      .orElse(null);
  }

  static String createSaveQuery(String tableName, BeanInfo beanInfo) {
    var properties = beanInfo.getPropertyDescriptors();
    return "MERGE INTO " + tableName + " " +
      Arrays.stream(properties)
        .filter(property -> !property.getName().equals("class"))
        .map(ORM::findColumnName)
        .collect(Collectors.joining(", ", "(", ")"))
      + " VALUES (" + String.join(", ", nCopies(properties.length - 1, "?")) + ");";
  }

  static Object save(
    Connection connection, String tableName, BeanInfo beanInfo, Object bean, PropertyDescriptor idProperty
  ) throws SQLException {
    var sqlQuery = createSaveQuery(tableName, beanInfo);
    try (var statement = connection.prepareStatement(sqlQuery, Statement.RETURN_GENERATED_KEYS)) {
      var index = 1;
      for (var property: beanInfo.getPropertyDescriptors()) {
        if (property.getName().equals("class")) continue;
        var getter = property.getReadMethod();
        var value = Utils.invokeMethod(bean, getter);
        statement.setObject(index++, value);
      }
      statement.executeUpdate();
      if (idProperty != null) {
        try (var resultSet = statement.getGeneratedKeys()) {
          if (resultSet.next()) {
            var key = resultSet.getObject(1);
            var setter = idProperty.getWriteMethod();
            Utils.invokeMethod(bean, setter, key);
          }
        }
      }
    }
    connection.commit();
    return bean;
  }

  public static <T extends Repository<?, ?>> T createRepository(Class<T> repositoryType) {
    var beanType = findBeanTypeFromRepository(repositoryType);
    var beanInfo = Utils.beanInfo(beanType);
    var constructor = Utils.defaultConstructor(beanType);
    var tableName = findTableName(beanType);
    var idProperty = findIdProperty(beanInfo);
    var idName = (idProperty == null)? null : findColumnName(idProperty);
    var findAllQuery = "SELECT * FROM " + tableName;
    var findByIdQuery = "SELECT * FROM " + tableName + " WHERE " + idName + " = ?";
    return repositoryType.cast(Proxy.newProxyInstance(repositoryType.getClassLoader(),
            new Class<?>[] {repositoryType}, (proxy, method, args) -> {
      var connection = currentConnection();
      try {
        return switch (method.getName()) {
          case "findAll" -> findAll(connection, findAllQuery, beanInfo, constructor);
          case "findById" -> findAll(connection, findByIdQuery, beanInfo, constructor, args[0]).stream().findFirst();
          case "save" -> save(connection, tableName, beanInfo, args[0], idProperty);
          case "equals", "hashCode", "toString" ->
                  throw new UnsupportedOperationException("method " + method.getName() + " not supported");
          default -> throw new IllegalStateException("method " + method.getName() + " not supported");
        };
      } catch (SQLException e) {
        throw new UncheckedSQLException(e);
      }
    }));
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
