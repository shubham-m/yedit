package shubhamm.yedit.contentassist;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.MarkedYAMLException;
import org.yaml.snakeyaml.events.AliasEvent;
import org.yaml.snakeyaml.events.CollectionStartEvent;
import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.MappingEndEvent;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.NodeEvent;
import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.events.SequenceEndEvent;
import org.yaml.snakeyaml.events.SequenceStartEvent;
import org.yaml.snakeyaml.parser.ParserException;


public class IncompleteYamlParser {
	
	private LinkedList path = new LinkedList();
	private LinkedList ambiguouslyRemoved = new LinkedList();
	private String leaf = null;
	private List<String> anchorList = new ArrayList();
	private MarkedYAMLException lastException = null;
	private boolean incomplete;
	private Object constructedYaml;
	private String type = null;
	
	public LinkedList getPath() {
		if(!path.isEmpty())
		{
			return path;
		}
		return ambiguouslyRemoved;
	}

	public String getLeaf() {
		return leaf==null?"":leaf;
	}

	public List<String> getAnchorList() {
		return anchorList;
	}

	public MarkedYAMLException getLastException() {
		return lastException;
	}

	public boolean isIncomplete() {
		return incomplete;
	}

	public Object getConstructedYaml() {
		return constructedYaml;
	}
	
	public void construct(Reader reader)
	{
		Yaml yaml = new Yaml();
		path.add("root");
		Iterator<Event> events = yaml.parse(reader).iterator();
		events.next(); // Skip processing StreamStartEvent
		events.next(); // Skip processing DocumentStartEvent
		try
		{
			constructedYaml =  construct(events);
		}
		catch(MarkedYAMLException ex)
		{
			lastException = ex;
		}
	}

	private Object construct(Iterator<Event> events)
	{
		Event currentEvent = events.next();
		return construct(currentEvent,events);
	}
	
	@SuppressWarnings("unchecked")
	private Object construct(Event event , Iterator<Event> laterEvents)
	{
		if(event instanceof NodeEvent && !(event instanceof AliasEvent) && ((NodeEvent)event).getAnchor()!=null)
		{
			anchorList.add(((NodeEvent)event).getAnchor());
		}
		
		if(event instanceof NodeEvent)
		{
			ambiguouslyRemoved.clear();
		}
		
		if(event instanceof ScalarEvent)
		{	
			try
			{
				String value = ((ScalarEvent) event).getValue();
				
				if(value!=null && value.length()>0)
				{
					if(leaf!=null)
					{
						path.add(leaf);
					}
					leaf = value;
				}
				else
				{
					value = null;
				}
				
				return value;
			}
			catch(ParserException e)
			{ 
				incomplete = true; 
				lastException = e;
				return null; 
			}
			
		}
		
		else if(event instanceof MappingStartEvent)
		{
			HashMap<String,Object> map = new HashMap<String,Object>();
			
			if(leaf instanceof String)
			{
				path.add(leaf); //If map is named add name to path
				leaf = null;
			}
			
			path.add("#map");
			
			try
			{
				event = laterEvents.next();
				while( !(event instanceof MappingEndEvent) && !incomplete )
				{
					String key = ((ScalarEvent) event).getValue(); //Complex Keys Not Supported
					leaf = key;
					Object value = null;
					
					try
					{
						value = construct(laterEvents);
					}
					catch(ParserException e)
					{
						incomplete = true;
						lastException = e;
					}
					
					if(value==null)
					{
						path.add(leaf);
						path.add("#value");
						leaf = null;
						incomplete = true;
					}
					else
					{
						map.put(key,value);
					}
					
					if(!incomplete)
					{
						event = laterEvents.next(); //Check for premature end before popping path
						if(value instanceof String && ((String)value).length()>0)
						{
							path.removeLast();
							leaf = null;
						}
					}
				}
			}
			catch(ParserException e)
			{ 
				if(e.getProblemMark().get_snippet().matches(".*,\\s*\\^$")) //Check if parsing ended after paring a ','
				{
					leaf=null;
				}
				
				if(!path.peekLast().equals("#map"))
				{
					if(leaf!=null && leaf.length()>0)
					{
						path.add("#value");
					}
					else
					{
						path.removeLast();
					}
				}
				incomplete = true;
				lastException = e;
			}
			
			if(!incomplete)
			{
				ambiguouslyRemoved.addFirst(path.removeLast());
				if(path.peekLast() instanceof String && !path.peekLast().toString().startsWith("#"))
				{
					ambiguouslyRemoved.addFirst(path.removeLast());
				}
				leaf = null;
				
				//If the mapping end was not marked by '}' then remember it as an ambiguous end
				MappingEndEvent mappingEnd = (MappingEndEvent) event;
				if(mappingEnd.getEndMark().getIndex() != mappingEnd.getStartMark().getIndex() )
				{
					ambiguouslyRemoved.clear();
				}
			}
			return map;
			
		}
		
		else if(event instanceof SequenceStartEvent)
		{
			ArrayList list = new ArrayList();
			if(leaf instanceof String)
			{
				path.add(leaf);
				leaf = null;
			}
			path.add("#seq");
			path.add(1);
			Event nextEvent = null;
			try
			{
				nextEvent = laterEvents.next();
				while(!incomplete && !(nextEvent instanceof SequenceEndEvent))
				{
					leaf = null;
					
					Object node = construct(nextEvent,laterEvents);
					list.add(node);
					
					if(!incomplete)
					{
						nextEvent = laterEvents.next(); //Check for premature end before popping path
						Object idx = path.removeLast();
						Integer index = (Integer) idx;
						path.add(++index);
					}
					
				}
			}
			catch(ParserException e)
			{ 
				if(e.getProblemMark().get_snippet().matches(".*,\\s*\\^$"))
				{
					//To determine occurrence of ','
					Object idx = path.removeLast();
					Integer index = (Integer) idx;
					path.add(++index);
					leaf=null;
				}
				incomplete = true;
				lastException = e;
			}
			if(!incomplete)
			{
				leaf = null;
				ambiguouslyRemoved.addFirst(path.removeLast()); //Pops out the index
				ambiguouslyRemoved.addFirst(path.removeLast()); //Pops out #seq 
				if(path.peekLast() instanceof String && !path.peekLast().toString().startsWith("#"))
				{
					ambiguouslyRemoved.addFirst(path.removeLast()); //Pops out list name if any
				}
				//If the mapping end was not marked by '}' then remember it as an ambiguous end
				SequenceEndEvent seqEnd = (SequenceEndEvent) nextEvent;
				if(seqEnd.getEndMark().getIndex() != seqEnd.getStartMark().getIndex() )
				{
					ambiguouslyRemoved.clear();
				}
				
			}
			return list;
		}
		
		return null;
	}

}