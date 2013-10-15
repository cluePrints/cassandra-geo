package com.clueprints.cg;

import org.cassandraunit.BaseCassandraUnit;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;
import com.netflix.astyanax.AstyanaxContext;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.connectionpool.NodeDiscoveryType;
import com.netflix.astyanax.connectionpool.impl.ConnectionPoolConfigurationImpl;
import com.netflix.astyanax.impl.AstyanaxConfigurationImpl;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.serializers.StringSerializer;
import com.netflix.astyanax.thrift.ThriftFamilyFactory;

public class FirstTest extends BaseCassandraUnit {
	
	public static ColumnFamily<String, String> CF_STANDARD1 = ColumnFamily
            .newColumnFamily("Standard1", StringSerializer.get(),
                    StringSerializer.get());
	@Test
	public void aaa() throws Exception {
		EmbeddedCassandraServerHelper.startEmbeddedCassandra();
		AstyanaxContext<Keyspace> ctx = new AstyanaxContext.Builder()
				.forKeyspace("MyKeyspace")
				.withAstyanaxConfiguration(
						new AstyanaxConfigurationImpl()
								.setDiscoveryType(NodeDiscoveryType.NONE))
				.withConnectionPoolConfiguration(
						new ConnectionPoolConfigurationImpl(
								"testConnectionPool").setPort(9160)
								.setMaxConnsPerHost(1)
								.setSeeds("127.0.0.1:9171"))
				.buildKeyspace(ThriftFamilyFactory.getInstance());
		ctx.start();
		Keyspace keyspace = ctx.getEntity();

		keyspace.createKeyspace(ImmutableMap
				.<String, Object> builder()
				.put("strategy_options",
						ImmutableMap.<String, Object> builder()
								.put("replication_factor", "1").build())
				.put("strategy_class", "SimpleStrategy").build());
		
		keyspace.prepareColumnMutation(CF_STANDARD1, "K1", "C1");
	}

	@Override
	protected void load() {

	}
}
