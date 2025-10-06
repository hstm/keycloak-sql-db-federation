# keycloak-database-federation

### Compatible with Keycloak 25.0.x+ Quarkus based.

Keycloak User Storage SPI for Relational Databases (Keycloak User Federation, supports postgresql, mysql, oracle and mysql).

- Keycloak User federation provider with SQL
- Keycloak User federation using existing database
- Keycloak database user provider
- Keycloak MSSQL Database Integration
- Keycloak SQL Server Database Integration
- Keycloak Oracle Database Integration
- Keycloak Postgres Database Integration
- Keycloak blowfish bcrypt support

## Requirements

- Keycloak 25.0.x
- Docker 4.10+
- OpenJDK 17+
- Maven 3.9.5

## Usage

1. Log into Keycloak's admin console
2. Add a new User Federation Provider; choose `sql-db-user-provider` from the list
3. Adjust the configuration as required (see configuration sample below)
4. If everything works properly, you can lookup for users from your external user database provided by the plugin.

## Configuration

### Sample Settings

#### Console display name

sql-db-user-provider

#### Connection String (example)

jdbc:sqlserver://192.168.1.89;databaseName=TestDB;trustServerCertificate=true

#### JDBC connection user

middleware

#### RDBMS

MS SQL Server (JDBC)

#### User count SQL query

select count(\*) from userLogin

#### List all users SQL query

select guiKundenId as id, kd.strKundenkuerzel as username, strEmail as email, name as lastName, Vorname as firstName from userLogin kd join customer k on kd.strKundenkuerzel = k.strKundenkuerzel

#### Find user by ID SQL query

select guiKundenId as id, kd.strKundenkuerzel as username, strEmail as email, name as lastName, Vorname as firstName, v.locale from userLogin kd join customer k on kd.strKundenkuerzel = k.strKundenkuerzel join (values (0, 'de'), (1, 'en'), (2, 'fr')) v(id, locale) on k.lngSpracheId = v.id where guiKundenId = ?

#### Find user by username or email SQL query

select guiKundenId as id, kd.strKundenkuerzel as username, strEmail as email, name as lastName, vorname as firstName, v.locale from userLogin kd join customer k on kd.strKundenkuerzel = k.strKundenkuerzel join (values (0, 'de'), (1, 'en'), (2, 'fr')) v(id, locale) on k.lngSpracheId = v.id cross join (select ? as login_name) const where kd.strKundenkuerzel = login_name or k.strEmail = login_name

#### Find user by username SQL query

select guiKundenId as id, kd.strKundenkuerzel as username, strEmail as email, name as lastName, vorname as firstName, v.locale from userLogin kd join customer k on kd.strKundenkuerzel = k.strKundenkuerzel join (values (0, 'de'), (1, 'en'), (2, 'fr')) v(id, locale) on k.lngSpracheId = v.id where kd.strKundenkuerzel = ?

#### Find user by search term SQL query

select guiKundenId as id, kd.strKundenkuerzel as username, strEmail as email, name as lastName, vorname as firstName, v.locale from userLogin kd join customer k on kd.strKundenkuerzel = k.strKundenkuerzel join (values (0, 'de'), (1, 'en'), (2, 'fr')) v(id, locale) on k.lngSpracheId = v.id cross join (select ? as login_name) const where kd.strKundenkuerzel = login_name or name = login_name or strEmail = login_name

#### Update user credentials

update userLogin set hash = ?, salt = ? where strKundenKuerzel = ?

#### Update user email address

update customer set strEmail = ? where strKundenKuerzel = ?

#### Find password hash for username or email SQL query

select hash, salt from userLogin kd join customer k on kd.strKundenkuerzel = k.strKundenkuerzel cross join (select ? as login_name) const where kd.strKundenkuerzel = login_name or strEmail = login_name

#### Find password hash SQL query (username only)

select hash, salt from userLogin where strKundenkuerzel = ?

#### Password hash function

SHA-512

#### Periodic full sync

off

#### Periodic changed users sync

off

#### Cache policy

DEFAULT

## Limitations

    - Do not support user roles our groups

## Custom attributes

Just add a mapper to client mappers with the same name as the returned column alias in your queries. Use mapper type "User Attribute".

## Build

    Before running the application, you need to configure `src/main/resources/application.properties` to match your environment. Update database credentials, ports, and other settings as needed. 

    - mvn clean package

    or if you want to use mvn wrapper:

    - mvn wrapper:wrapper
    - ./mvnw clean package


## Deployment

    1) Copy every  `.jar` from dist/ folder  to  /providers folder under your keycloak installation root.
        - i.e, on a default keycloak setup, copy all  `.jar` files to <keycloak_root_dir>/providers
    2) run :
        $ ./bin/kc.sh start-dev
    OR if you are using a production configuration:
        $ ./bin/kc.sh build
        $ ./bin/kc.sh start

## For further information, see:

    - https://www.keycloak.org/docs/latest/server_development/#packaging-and-deployment

    [!IMPORTANT]
    Before running the application, you need to configure `src/main/resources/application.properties` to match your environment. Update database credentials, ports, and other settings as needed (e. g. CORS). 

# Docker setup (for development purposes only)

1. Create a docker network

   `$ docker network create demo-network`

2. Create and start a Keycloak container in dev-mode

   `$ docker run -p 9080:9080 --name keycloak --net demo-network -e KEYCLOAK_ADMIN=admin -e KEYCLOAK_ADMIN_PASSWORD=admin quay.io/keycloak/keycloak:20.0.0 start-dev --http-port 9080`

3. Create and start a Mailhog container

   `$ docker run -d -p 8025:8025 --name demo-mail --net demo-network mailhog/mailhog`

   (Mailhog has its own frontend, see [http://localhost:8025/](http://localhost:8025/))

4. Open [http://localhost:8080/](http://localhost:8080/), log in as admin
5. Import the [realm configuration](realm-export.json) (Manage -> Import)
6. Copy all files from the `dist` directory to Keycloak's `providers` directory within the docker container, either manually or by executing the script `docker_cp_jars.sh` from within the project directory.
7. Restart Keycloak

   `$ docker restart Keycloak`

8. Start the JS Test Console

   `docker build -t demo-js-console js-console`

   `docker run --name demo-js-console -p 8000:80 demo-js-console`

9. Open the JS Test Console [http://localhost:8000/](http://localhost:8000/)`
10. If anything doesn't work as expected, just check the logs, either via the Docker app, or via command line

    `$ docker logs keycloak > log.txt`
