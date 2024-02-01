package com.project.chatting.chat.service;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.stereotype.Service;

import com.project.chatting.chat.entity.Chat;
import com.project.chatting.chat.repository.ChatRepository;
import com.project.chatting.chat.repository.ChatRoomRepository;
import com.project.chatting.chat.request.ChatReadRequest;
import com.project.chatting.chat.request.ChatRequest;
import com.project.chatting.chat.response.ChatResponse;
import com.project.chatting.chat.response.ChatRoomResponse;
import com.project.chatting.chat.request.CreateJoinRequest;
import com.project.chatting.chat.request.CreateRoomRequest;
import com.project.chatting.chat.response.CreateRoomResponse;
import com.project.chatting.user.repository.UserRepository;


@Service
public class ChatService {
	@Autowired
	private UserRepository userRepository;
	
	@Autowired
	private ChatRoomRepository chatRoomRepository;

	@Autowired
	private ChatRepository chatRepository;
	
	@Autowired
  private RedisTemplate<String, Object> redisTemplate;
	
	@Autowired
	  private RedisTemplate<String, ChatRequest> redisChatTemplate;
	
	public ChatResponse insertMessage(ChatRequest req) {
		// 시간 score로 관리하기 위해 숫자로 변환
		String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
		Long now_long = Long.parseLong(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
		// 현재 시간 set
		req.setCreateAt(now);
		int allMember = chatRepository.getChatMemberCnt(req.getRoomId());
		int connectMember = chatRoomRepository.getUserCount(Integer.toString(req.getRoomId())).length;
		
		// 안읽음 숫자
		req.setReadCnt(allMember - connectMember);
		req.setUsers(Arrays.asList(chatRoomRepository.getUserCount(Integer.toString(req.getRoomId()))));
		
        // redis messageData 저장
		ZSetOperations<String, ChatRequest> zSetOperations = redisChatTemplate.opsForZSet();
		zSetOperations.add("roomId:"+req.getRoomId(), req, now_long);
		
		//System.out.println(zSetOperations.range("ZKey", 0, -1));
		ChatResponse res = ChatResponse.toDto(req);
		
		return res;
	}
	
	public void setMessages() {
		Set<String> keys = redisTemplate.keys("roomId:*");
		Iterator<String> it = keys.iterator();
		
		while(it.hasNext()) {
			List<ChatRequest> li = new ArrayList<>();
			
			String data = it.next();
			
			ZSetOperations<String, ChatRequest> zSetOperations = redisChatTemplate.opsForZSet();
			
			Set<ChatRequest> list = zSetOperations.range(data, 0, -1);

			list.forEach(li::add);

			for (int i=0; i < li.size(); i++) {
				int roomId = li.get(i).getRoomId();
				String createAt = li.get(i).getCreateAt();
				String creater = li.get(i).getUserId();
				
				chatRepository.setChatMessage(li.get(i));
				List<String> ss = chatRepository.getRoomMember(roomId);
				List<String> us = li.get(i).getUsers();
				//리스트 합치기
				List<String> join = Stream.concat(ss.stream(), us.stream())
						.distinct().collect(Collectors.toList());
				
				List<ChatReadRequest> listmap = new ArrayList<>();
				
				join.forEach(item -> {
					Map<String, String> map = new HashMap<String, String>();
					
					map.put("creater", creater);
					map.put("id", item);
					map.put("yn", us.contains(item) ? "1" : "0");
					map.put("at", createAt);
					
					listmap.add(new ChatReadRequest(roomId, map));
				});

				chatRepository.setChatRead(listmap);
				
			}

		}
	}

	// 채팅방 조회
	public int existChatRoom(CreateRoomRequest createRoomRequest){
		createRoomRequest.setUserCount(createRoomRequest.getUserId().size());
		Collections.sort(createRoomRequest.getUserId());

		String users = createRoomRequest.getUserId().stream().collect(Collectors.joining(","));
		System.out.println("Users : " + users);

		String result = chatRepository.findChatRoomByUserId(users); 

		return result == null ? -1 : Integer.parseInt(result) ;
	}

	// 채팅방 생성
	public CreateRoomResponse createRoom(CreateRoomRequest createRoomRequest){
		chatRepository.setChatRoom(createRoomRequest);
		
		CreateRoomResponse createRoomResponse = CreateRoomResponse.toDto(createRoomRequest.getRoomId());
		return createRoomResponse;
	}

	// 채팅방 참여 생성
	public void createJoin(List<CreateJoinRequest> createJoinRequest){
		chatRepository.setChatJoin(createJoinRequest);
	}

	// 채팅방 나가기

	// 채팅방 목록 조회
   	public List<ChatRoomResponse> findAll(String userId) {
        return chatRepository.selectChatRoomList(userId);
    }

}
