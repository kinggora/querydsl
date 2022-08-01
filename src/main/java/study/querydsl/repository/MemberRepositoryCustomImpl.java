package study.querydsl.repository;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.util.StringUtils;
import study.querydsl.dto.MemberSearchCondition;
import study.querydsl.dto.MemberTeamDto;
import study.querydsl.dto.QMemberTeamDto;

import javax.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static study.querydsl.entity.QMember.member;
import static study.querydsl.entity.QTeam.team;

public class MemberRepositoryCustomImpl implements MemberRepositoryCustom{

    private final JPAQueryFactory queryFactory;

    public MemberRepositoryCustomImpl(EntityManager em) {
        queryFactory = new JPAQueryFactory(em);
    }

    @Override
    public List<MemberTeamDto> search(MemberSearchCondition memberCond){
        return queryFactory
                .select(new QMemberTeamDto(
                        member.id,
                        member.username,
                        member.age,
                        team.id,
                        team.name
                ))
                .from(member)
                .leftJoin(member.team, team)
                .where(
                        usernameEq(memberCond.getUsername()),
                        teamNameEq(memberCond.getTeamName()),
                        ageGoe(memberCond.getAgeGoe()),
                        ageLoe(memberCond.getAgeLoe())
                )
                .orderBy(member.username.desc()) //정적 정렬
                .fetch();
    }

    @Override
    public Page<MemberTeamDto> searchPage(MemberSearchCondition memberCond, Pageable pageable) {
        List<MemberTeamDto> content = queryFactory
                .select(new QMemberTeamDto(
                        member.id,
                        member.username,
                        member.age,
                        team.id,
                        team.name
                ))
                .from(member)
                .leftJoin(member.team, team)
                .where(
                        usernameEq(memberCond.getUsername()),
                        teamNameEq(memberCond.getTeamName()),
                        ageGoe(memberCond.getAgeGoe()),
                        ageLoe(memberCond.getAgeLoe())
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(getOrderSpecifier(pageable.getSort()).stream()
                        .toArray(OrderSpecifier[]::new))                //동적 정렬. orderBy(OrderSpecifier...)
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(member.count())
                .from(member)
                .leftJoin(member.team, team)
                .where(
                        usernameEq(memberCond.getUsername()),
                        teamNameEq(memberCond.getTeamName()),
                        ageGoe(memberCond.getAgeGoe()),
                        ageLoe(memberCond.getAgeLoe())
                );

        //return new PageImpl<>(content, pageable, totalCount);
        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    /**
     * OrderSpecifier(Order order, Expression<T> target)
     * order: Pageable 객체에 포함된 순서 정보. (오름차, 내림차) ex.Order.ASC, Order.DESC
     * target: 정렬 기준 컬럼의 path 정보. (엔티티 클래스.엔티티 컬럼) ex.Member.username
     */
    private List<OrderSpecifier> getOrderSpecifier(Sort sort) {
        List<OrderSpecifier> orders = new ArrayList<>();
        for(Sort.Order o : sort){
            PathBuilder pathBuilder = new PathBuilder(member.getType(), member.getMetadata()); //QMember.member: EntityPath<Member>
            OrderSpecifier orderSpecifier = new OrderSpecifier(o.isAscending() ? Order.ASC : Order.DESC,
                    pathBuilder.get(o.getProperty()));
            orders.add(orderSpecifier);
        }
        return orders;
    }

    private BooleanExpression ageLoe(Integer ageLoe) {
        return ageLoe == null? null : member.age.loe(ageLoe);
    }

    private BooleanExpression ageGoe(Integer ageGoe) {
        return ageGoe == null? null : member.age.goe(ageGoe);
    }

    private BooleanExpression teamNameEq(String teamName) {
        return StringUtils.hasText(teamName)? team.name.eq(teamName) : null;
    }

    private BooleanExpression usernameEq(String username) {
        return StringUtils.hasText(username)? member.username.eq(username) : null;
    }
}


