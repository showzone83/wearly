package app.wearly.wardrobe;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.domain.Category;
import app.wearly.domain.ColorKey;
import app.wearly.domain.ItemStatus;
import app.wearly.domain.Pattern;
import app.wearly.domain.Season;
import app.wearly.domain.StyleTag;
import app.wearly.domain.SubType;
import app.wearly.support.IntegrationTestBase;
import app.wearly.user.User;
import app.wearly.user.UserRepository;
import java.util.List;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 계약 2장: JPA와 MyBatis는 같은 DataSource·트랜잭션 매니저를 쓴다. */
class JpaMyBatisTransactionIntegrationTest extends IntegrationTestBase {

    @Autowired
    ApplicationContext context;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    DataSource dataSource;

    @Autowired
    SqlSessionFactory sqlSessionFactory;

    @Autowired
    UserRepository users;

    @Autowired
    WardrobeItemRepository items;

    @Autowired
    WardrobeQueryMapper queries;

    @Test
    void 트랜잭션_매니저와_DataSource는_하나다() {
        assertThat(context.getBeansOfType(PlatformTransactionManager.class)).hasSize(1);
        assertThat(transactionManager).isInstanceOf(JpaTransactionManager.class);
        assertThat(sqlSessionFactory.getConfiguration().getEnvironment().getDataSource()).isSameAs(dataSource);
    }

    @Test
    void 같은_트랜잭션에서_JPA가_쓴_값을_MyBatis가_읽고_롤백하면_함께_사라진다() {
        long userId = users.save(new User(uniqueEmail(), "트랜잭션")).getId();
        ItemFilter all = new ItemFilter(userId, null, null, null, null, "recent");
        ItemInput input = new ItemInput("네이비 니트", Category.TOP, SubType.KNIT, ColorKey.NAVY, Pattern.SOLID,
                List.of(Season.FALL), List.of(StyleTag.NEAT), 2, ItemStatus.OWNED, null, 0, null);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            WardrobeItem item = new WardrobeItem(userId);
            ItemMapping.apply(item, input);
            items.saveAndFlush(item);
            // 아직 커밋 전: 같은 연결을 쓰므로 MyBatis 조회에 보인다
            assertThat(queries.findItems(all)).hasSize(1);
            status.setRollbackOnly();
        });

        assertThat(queries.findItems(all)).isEmpty();
    }
}
