package shubhamm.yedit.contentassist;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.events.AliasEvent;
import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.MappingEndEvent;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.NodeEvent;
import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.events.SequenceEndEvent;
import org.yaml.snakeyaml.events.SequenceStartEvent;
import org.yaml.snakeyaml.parser.ParserException;


public class IncompleteYamlParser {
	
	public LinkedList path = new LinkedList();
	public String leaf = null;
	public List<String> anchorList = new ArrayList();
	public ParserException exception;
	private boolean incomplete;
	
	public Object construct(Reader reader)
	{
		Yaml yaml = new Yaml();
		Iterator<Event> events = yaml.parse(reader).iterator();
		events.next();
		events.next();
		Object constructed =  construct(events);
		if(leaf == null)
		{
			leaf = "";
		}
		return constructed;
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
		
		if(event instanceof ScalarEvent)
		{	
			try
			{
				String value = ((ScalarEvent) event).getValue();
				if(leaf == null)
				{
					leaf = value;
				}
				else if(value.length()>0)
				{
					path.add(leaf);
					leaf = value;
				}
				else if(value.equals(""))
				{
					value = null;
				}
				return value;
			}
			catch(ParserException e)
			{ 
				incomplete = true; 
				exception = e;
				return null; 
			}
			
		}
		else if(event instanceof MappingStartEvent)
		{
			HashMap<String,Object> map = new HashMap<String,Object>();
			if(leaf instanceof String)
			{
				path.add(leaf);
				leaf = null;
			}
			/*extra*/path.add("#map");
			try
			{
				event = laterEvents.next();
				while( !(event instanceof MappingEndEvent) && !incomplete )
				{
					String key = ((ScalarEvent) event).getValue();
					leaf = key;
					Object value = null;
					try
					{
						value = construct(laterEvents);
					}
					catch(ParserException e)
					{
						incomplete = true;
						exception = e;
					}
					if(value==null)
					{
						path.add(leaf);
						/*extra*/path.add("#value");
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
				if(e.getProblemMark().get_snippet().matches(".*,\\s*\\^$"))
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
				exception = e;
			}
			if(!incomplete)
			{
				/*extra*/path.removeLast();
				if(path.peekLast() instanceof String && !path.peekLast().toString().startsWith("#"))
				{
					path.removeLast();
				}
				leaf = null;
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
			/*extra*/path.add("#seq");
			path.add(1);
			try
			{
				Event nextEvent = laterEvents.next();
				while(!incomplete && !(nextEvent instanceof SequenceEndEvent))
				{
					leaf = null;
					
					Object node = construct(nextEvent,laterEvents);
					list.add(node);
					
					if(!incomplete)
					{
						nextEvent = laterEvents.next();
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
				exception = e;
			}
			if(!incomplete)
			{
				leaf = null;
				path.removeLast(); //Pops out index
				/*extra*/path.removeLast(); //Pops out #seq 
				if(path.peekLast() instanceof String && !path.peekLast().toString().startsWith("#"))
				{
					path.removeLast(); //Pops out list name if any
				}
			}
			return list;
		}
		else if(event instanceof AliasEvent)
		{
			
		}
		return null;
	}

}