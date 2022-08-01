package study.querydsl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.dto.MemberDto;
import study.querydsl.dto.QMemberDto;
import study.querydsl.dto.UserDto;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.Team;

import javax.persistence.EntityManager;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static study.querydsl.entity.QMember.member;

@SpringBootTest
@Transactional
public class QuerydslAdvancedTest {

    @Autowired
    EntityManager em;

    JPAQueryFactory queryFactory;

    @BeforeEach
    void init(){
        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");

        em.persist(teamA);
        em.persist(teamB);

        Member member1 = new Member("member1", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);
        Member member3 = new Member("member3", 30, teamB);
        Member member4 = new Member("member4", 40, teamB);

        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);

        queryFactory = new JPAQueryFactory(em);
    }

    /**
     * 프로젝션 - DTO 조회
     * 1. 순수 JPA
     */
    @Test
    void 순수JPA(){
        List<MemberDto> result = em.createQuery("select new study.querydsl.dto.MemberDto(m.username, m.age)"
                        + " from Member m", MemberDto.class)
                .getResultList();

        assertThat(result.size()).isEqualTo(4);
    }

    /**
     * 프로젝션 - DTO 조회
     * 2. Querydsl Bean Population
     *  -property
     */
    @Test
    void 프로퍼티_접근(){
        List<MemberDto> result = queryFactory
                .select(Projections.bean(MemberDto.class,
                        member.username, member.age))
                .from(member)
                .fetch();

        assertThat(result.size()).isEqualTo(4);
    }

    /**
     * 프로젝션 - DTO 조회
     * 2. Querydsl Bean Population
     *  -field
     */
    @Test
    void 필드_직접_접근(){
        List<MemberDto> result = queryFactory
                .select(Projections.fields(MemberDto.class, member.username, member.age))
                .from(member)
                .fetch();

        assertThat(result.size()).isEqualTo(4);
    }

    /**
     * 프로젝션 - DTO 조회
     * 2. Querydsl Bean Population
     *  -property & field
     *  -별칭이 다를 때
     */
    @Test
    void differentAlias() {
        QMember memberSub = new QMember("memberSub");
        List<UserDto> result = queryFactory
                .select(Projections.fields(UserDto.class,
                                member.username.as("name"),
                                ExpressionUtils.as(
                                        JPAExpressions.select(memberSub.age.max())
                                                .from(memberSub), "age")
                        )
                ).from(member)
                .fetch();

        for (UserDto userDto : result) {
            System.out.println("userDto = " + userDto);
        }
    }

    /**
     * 프로젝션 - DTO 조회
     * 2. Querydsl Bean Population
     *  -constructor
     */
    @Test
    void 생성자_사용(){
        List<MemberDto> result = queryFactory
                .select(Projections.constructor(MemberDto.class,
                        member.username, member.age))
                .from(member)
                .fetch();

        assertThat(result.size()).isEqualTo(4);
    }

    /**
     * 프로젝션 - DTO 조회
     * 3. 생성자 + @QueryProjection
     */
    @Test
    void queryProjection(){

        List<MemberDto> result = queryFactory
                .select(new QMemberDto(member.username, member.age))
                .from(member)
                .fetch();

        assertThat(result.size()).isEqualTo(4);
    }

    /**
     * 동적 쿼리
     * -BooleanBuilder
     */
    @Test
    void booleanBuilder() throws Exception {
        String usernameParam = "member1";
        Integer ageParam = 10;

        List<Member> result = searchMember1(usernameParam, ageParam);

        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember1(String usernameCond, Integer ageCond) {
        BooleanBuilder builder = new BooleanBuilder();

        if(usernameCond != null)
            builder.and(usernameEq(usernameCond));
        if(ageCond != null)
            builder.and(ageEq(ageCond));

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(builder)
                .fetch();

        return result;
    }

    /**
     * 동적 쿼리
     * -Where 다중 파라미터
     */
    @Test
    void whereParam() throws Exception{
        String usernameParam = "member1";
        Integer ageParam = 10;

        List<Member> result = searchMember2(usernameParam, ageParam);

        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember2(String usernameParam, Integer ageParam) {

        return queryFactory
                .selectFrom(member)
                .where(usernameEq(usernameParam), ageEq(ageParam))
                .fetch();
    }

    private BooleanExpression usernameEq(String usernameCond) {
        return usernameCond != null? member.username.eq(usernameCond) : null;
    }

    private BooleanExpression ageEq(Integer ageCond) {
        return ageCond != null? member.age.eq(ageCond) : null;
    }

    /**
     * 벌크 연산
     * -수정, 삭제
     */
    @Test
    void bulk(){
        long updateCount = queryFactory
                .update(member)
                .set(member.username, "비회원")
                .where(member.age.lt(28))
                .execute();

        assertThat(updateCount).isEqualTo(2);

        queryFactory
                .update(member)
                .set(member.age, member.age.add(1)) //곱하기: multiply()
                .execute();

        long deleteCount = queryFactory
                .delete(member)
                .where(member.age.gt(18))
                .execute();
        assertThat(deleteCount).isEqualTo(3);
    }

    /**
     * SQL function 호출하기
     */
    @Test
    void sqlFunc(){
        String replace = queryFactory
                .select(Expressions.stringTemplate("function('replace', {0}, {1}, {2})",
                        member.username, "member", "M"))
                .from(member)
                .fetchFirst();
        System.out.println("replace = " + replace);

        List<Member> lower = queryFactory
                .select(member)
                .from(member)
                .where(member.username.eq(Expressions.stringTemplate("function('lower', {0})",
                        member.username)))
                .fetch();

        for (Member m : lower) {
            System.out.println("member = " + m);
        }
    }
}
