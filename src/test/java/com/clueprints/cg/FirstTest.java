package com.clueprints.cg;

 import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.List;

import javax.swing.JFrame;

import org.apache.commons.lang.Validate;
import org.cassandraunit.BaseCassandraUnit;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.gradle.jarjar.com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.beoui.geocell.GeocellManager;
import com.beoui.geocell.GeocellQueryEngine;
import com.beoui.geocell.GeocellUtils;
import com.beoui.geocell.model.BoundingBox;
import com.beoui.geocell.model.GeocellQuery;
import com.beoui.geocell.model.LocationCapable;
import com.beoui.geocell.model.Point;
import com.google.common.collect.ImmutableMap;
import com.netflix.astyanax.AstyanaxContext;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.MutationBatch;
import com.netflix.astyanax.connectionpool.NodeDiscoveryType;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.connectionpool.impl.ConnectionPoolConfigurationImpl;
import com.netflix.astyanax.impl.AstyanaxConfigurationImpl;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.model.Row;
import com.netflix.astyanax.model.Rows;
import com.netflix.astyanax.serializers.StringSerializer;
import com.netflix.astyanax.thrift.ThriftFamilyFactory;

public class FirstTest extends BaseCassandraUnit {
	
	public static ColumnFamily<String, Point> INDEX_CF = ColumnFamily.newColumnFamily("Standard1", StringSerializer.get(), PointSerializer.get(), StringSerializer.get());
	
	AstyanaxContext<Keyspace> ctx;
	Keyspace keyspace;
	
	int scale = 4;
	final Image image = new BufferedImage(360*scale, 180*scale, BufferedImage.TYPE_INT_RGB);
	
	JFrame frame = new JFrame() {
		public void paint(Graphics g) {
			g.drawImage(image, 0, 0, null);
		};
	};
	
	@Before
	public void before() throws Exception
	{

		frame.setSize(360*scale, 180*scale);
		frame.setLocation(0, 0);
		frame.setVisible(true);
		frame.setAlwaysOnTop(true);	
		
		
		EmbeddedCassandraServerHelper.startEmbeddedCassandra();
		ctx = new AstyanaxContext.Builder()
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
		keyspace = ctx.getEntity();

		keyspace.createKeyspace(ImmutableMap
				.<String, Object> builder()
				.put("strategy_options",
						ImmutableMap.<String, Object> builder()
								.put("replication_factor", "1").build())
				.put("strategy_class", "SimpleStrategy").build());
		keyspace.createColumnFamily(INDEX_CF, null);

	}
	
	// geocoding type
	@Test
	public void aaa() throws Exception {		
		Point point = new Point(1, 15);
		String entityId = "hehehe";		
		
		// row:string -> column:point -> entityId:?
			
		addToGeoIndex(point, entityId);
		
		double delta = 40;
		draw(Color.YELLOW, new BoundingBox(point.getLat()-delta, point.getLon()-delta, point.getLat()+delta, point.getLon()+delta));
		double maxDistance = 6378135;
		List<GeoIndexSearchResult> search = geoIndexProximityLookup(new Point(point.getLat() + delta, point.getLon() + delta), maxDistance, 5);
		Assert.assertThat(search, not(empty()));
	}
	
	private void draw(String cc1, Color color) {
		BoundingBox b1 = GeocellUtils.computeBox(cc1);
		draw(color, b1);
	}

	private void draw(Color color, BoundingBox b1) {
		Graphics g = image.getGraphics();		
		g.setColor(color);
		int x = (int)(Math.round((180 + Math.min(b1.getWest(), b1.getEast()))) % 360)*scale;
		int y = (int)(Math.round((90-Math.max(b1.getNorth(), b1.getSouth()))) % 180)*scale;
		int w = (int)Math.round((Math.max(b1.getWest(),b1.getEast()) - Math.min(b1.getWest(),b1.getEast()))*scale);
		int h = (int)Math.round((Math.max(b1.getSouth(), b1.getNorth()) - Math.min(b1.getSouth(),  b1.getNorth()))*scale);
		System.out.println(x+","+y+".."+(x+w)+","+(y+h));
		System.out.println(b1);
		g.drawRect(
				x,
				y, 
				w, 
				h);
	}

	private List<GeoIndexSearchResult> geoIndexProximityLookup(Point point, double maxDistanceMeters, int maxResults) {
		GeocellQueryEngine queryEngine = new GeocellQueryEngine(){
			@Override
			public <T> List<T> query(GeocellQuery baseQuery, List<String> curGeocellsUnique, Class<T> entityClass) {
				try{
					Rows<String, Point> rows = keyspace.prepareQuery(INDEX_CF)
						.getKeySlice(curGeocellsUnique)
						.execute().getResult();
					for (String c : curGeocellsUnique) {
						draw(c, Color.green);
					}
					List<GeoIndexSearchResult> results = Lists.newLinkedList();
					for (Row<String, Point> row : rows) {
						for (Column<Point> c : row.getColumns()) {
							results.add(new GeoIndexSearchResult(c.getName(), c.getByteBufferValue()));
						}
					}
					return (List<T>)results;					
				} catch (Exception ex){
					throw new RuntimeException(ex);
				}
			}
		};
		
		int maxGeocellResolution = 13;
		List<GeoIndexSearchResult> search = GeocellManager.proximitySearch(point, maxResults, maxDistanceMeters, GeoIndexSearchResult.class, null, queryEngine, maxGeocellResolution);
		return search;
	}

	private void addToGeoIndex(Point p, String entityId)
			throws ConnectionException {
		MutationBatch batch = keyspace.prepareMutationBatch();		
		for (String idx : GeocellManager.generateGeoCell(p))
		{
			System.err.println(idx);
			batch.withRow(INDEX_CF, idx).putColumn(p, entityId);
		}
		
		batch.execute();
	}
	
	public static class GeoIndexSearchResult implements LocationCapable
	{
		private final Point point;
		private final ByteBuffer key;
		public GeoIndexSearchResult(Point point, ByteBuffer key) {
			super();
			this.point = point;
			Validate.notNull(key);
			this.key = key;
		}
		
		@Override
		public Point getLocation() {
			return point;
		}
		
		@Override
		public List<String> getGeocells() {
			throw new IllegalStateException("Not implemented");
		}
		
		@Override
		public String getKeyString() {
			return StringSerializer.get().fromByteBuffer(key);
		}
	}
	
	@Override
	protected void load() {

	}
}
